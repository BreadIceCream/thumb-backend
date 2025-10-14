package com.bread.breadthumb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bread.breadthumb.constant.Constant;
import com.bread.breadthumb.exception.BusinessException;
import com.bread.breadthumb.model.dto.DoThumbRequest;
import com.bread.breadthumb.model.entity.Blog;
import com.bread.breadthumb.model.entity.Thumb;
import com.bread.breadthumb.model.entity.User;
import com.bread.breadthumb.service.BlogService;
import com.bread.breadthumb.service.ThumbService;
import com.bread.breadthumb.mapper.ThumbMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
* @author huang
* @description 针对表【thumb】的数据库操作Service实现
* @createDate 2025-10-12 16:02:41
*/
@Service
@Slf4j
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService{

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private BlogService blogService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 校验参数和用户登录态
        // 加锁，当前登录用户的id作为锁对象，确保只有一个线程在操作当前对象
        // 使用编程式事务执行逻辑。事务要在锁内开启，确保下一个线程获取到锁时事务已经完成提交。
        User loginUser = (User) request.getSession().getAttribute(Constant.LOGIN_USER);
        if (loginUser == null || doThumbRequest == null || doThumbRequest.getBlogId() == null){
            throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST, Constant.THUMB_DOTHUMB_INVALID_PARAMS);
        }
        synchronized (loginUser.getId().toString().intern()){
            return transactionTemplate.execute(status -> {
                // 判断是否已经点赞，基于redis实现
                Long blogId = doThumbRequest.getBlogId();
                Long userId = loginUser.getId();
                Boolean thumbed = this.hasThumbRedis(blogId, userId);
                if (thumbed){
                    throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST, Constant.THUMB_DOTHUMB_ALREADY_THUMBED);
                }
                // 创建thumb插入数据库，更新blog表点赞数，将点赞记录存入redis中，若是热点blog则修改redis中的点赞数
                // 数据库的锁机制保证了并发安全性，修改数据时，当前事务会获取X锁，直到commit或rollback才释放
                // 也就是说从update语句开始，直到代码块结束，该事务持有blogId这一行数据的X锁，
                // 从而保证了下一个事务在执行以下代码块时，前一个事务的执行结果具有可见性。因此修改redis中blog的点赞数不需要加锁。
                boolean update = blogService.lambdaUpdate().eq(Blog::getId, blogId).setSql("thumbCount = thumbCount + 1").update();
                Thumb thumb = new Thumb();
                thumb.setUserId(userId);
                thumb.setBlogId(blogId);
                boolean success = update && save(thumb);
                if (success) {
                    redisTemplate.opsForHash().put(Constant.REDIS_USER_THUMB_KEY_PREFIX + userId, blogId.toString(), thumb.getId());
                    Object thumbCount = redisTemplate.opsForHash().get(Constant.REDIS_BLOG_KEY_PREFIX + blogId, "thumbCount");
                    if (thumbCount != null){
                        log.info("Redis Blog {} thumb count add...", blogId);
                        redisTemplate.opsForHash().put(Constant.REDIS_BLOG_KEY_PREFIX + blogId, "thumbCount", Long.parseLong(thumbCount.toString()) + 1);
                    }
                }
                return success;
            });
        }
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 校验参数和用户登录态
        // 加锁，当前登录用户的id作为锁对象，确保只有一个线程在操作当前对象
        // 使用编程式事务执行逻辑。事务要在锁内开启，确保下一个线程获取到锁时事务已经完成提交。
        User loginUser = (User) request.getSession().getAttribute(Constant.LOGIN_USER);
        if (loginUser == null || doThumbRequest == null || doThumbRequest.getBlogId() == null){
            throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST, Constant.THUMB_DOTHUMB_INVALID_PARAMS);
        }
        synchronized (loginUser.getId().toString().intern()){
            return transactionTemplate.execute(status -> {
                // 判断是否已经点赞，基于redis实现
                Long blogId = doThumbRequest.getBlogId();
                Long userId = loginUser.getId();
                Object thumbIdObj = redisTemplate.opsForHash().get(Constant.REDIS_USER_THUMB_KEY_PREFIX + userId, blogId.toString());
                if (thumbIdObj == null) {
                    throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST, Constant.THUMB_DOTHUMB_NOT_THUMBED);
                }
                Long thumbId = Long.valueOf(thumbIdObj.toString());
                // 删除thumb表数据，同时更新blog表点赞个数
                boolean update = blogService.lambdaUpdate().eq(Blog::getId, blogId).setSql("thumbCount = thumbCount - 1").update();
                boolean success = update && removeById(thumbId);
                // 删除redis中的点赞数据，若是热点blog则更新redis中的blog点赞数
                if (success) {
                    redisTemplate.opsForHash().delete(Constant.REDIS_USER_THUMB_KEY_PREFIX + userId, blogId.toString());
                    Object thumbCount = redisTemplate.opsForHash().get(Constant.REDIS_BLOG_KEY_PREFIX + blogId, "thumbCount");
                    if (thumbCount != null){
                        log.info("Redis Blog {} thumb count minus...", blogId);
                        redisTemplate.opsForHash().put(Constant.REDIS_BLOG_KEY_PREFIX + blogId, "thumbCount", Long.parseLong(thumbCount.toString()) - 1);
                    }
                }
                return success;
            });
        }
    }

    /**
     * 查询redis，判断用户是否已经点赞
     * @param blogId
     * @param userId
     * @return
     */
    @Override
    public Boolean hasThumbRedis(Long blogId, Long userId) {
        log.info("Check thumb using redis.Blog {}, User {}...", blogId, userId);
        return redisTemplate.opsForHash().hasKey(Constant.REDIS_USER_THUMB_KEY_PREFIX + userId, blogId.toString());
    }

    /**
     * 批量查询redis，判断用户是否已经点赞
     * @param blogIds
     * @param userId
     * @return ThumbId列表
     */
    @Override
    public List<Object> hasThumbRedis(List<Long> blogIds, Long userId) {
        log.info("Check thumbs using redis.Blog ids {}, User {}...", blogIds, userId);
        List<Object> list = blogIds.stream().map(Object::toString).collect(Collectors.toList());
        return redisTemplate.opsForHash().multiGet(Constant.REDIS_USER_THUMB_KEY_PREFIX + userId, list);
    }

    /**
     * 查询数据库，判断用户是否已经点赞
     * @param blogId
     * @param userId
     * @return
     */
    @Override
    public Boolean hasThumbDb(long blogId, Long userId) {
        return lambdaQuery().eq(Thumb::getBlogId, blogId).eq(Thumb::getUserId, userId).exists();
    }

    /**
     * 批量查询数据库，判断用户是否已经点赞
     * @param blogIds
     * @param userId
     * @return
     */
    @Override
    public List<Thumb> hasThumbDb(List<Long> blogIds, Long userId) {
        return lambdaQuery().eq(Thumb::getUserId, userId).in(Thumb::getBlogId, blogIds).list();
    }
}




