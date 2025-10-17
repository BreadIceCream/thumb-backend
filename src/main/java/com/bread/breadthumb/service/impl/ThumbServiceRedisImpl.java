package com.bread.breadthumb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bread.breadthumb.constant.Constant;
import com.bread.breadthumb.exception.BusinessException;
import com.bread.breadthumb.mapper.ThumbMapper;
import com.bread.breadthumb.model.dto.DoThumbRequest;
import com.bread.breadthumb.model.entity.Thumb;
import com.bread.breadthumb.model.entity.User;
import com.bread.breadthumb.model.enums.LuaStatusEnum;
import com.bread.breadthumb.service.BlogService;
import com.bread.breadthumb.service.ThumbService;
import com.bread.breadthumb.service.UserService;
import com.bread.breadthumb.util.RedisKeyUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
* @author huang
* @description 针对表【thumb】的数据库操作Service实现
* @createDate 2025-10-12 16:02:41
*/
@Service("thumbServiceRedis") // thumb记录先通过redis保存到临时记录中，使用定时任务每10s同步到数据库
@Slf4j
public class ThumbServiceRedisImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService{

    @Autowired
    private BlogService blogService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private UserService userService;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 校验参数和用户登录态
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null || doThumbRequest == null || doThumbRequest.getBlogId() == null){
            throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST, Constant.THUMB_DOTHUMB_INVALID_PARAMS);
        }
        // 准备redis的key
        Long userId = loginUser.getId();
        Long blogId = doThumbRequest.getBlogId();
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(RedisKeyUtil.getTimeSlice());
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        String blogKey = RedisKeyUtil.getBlogKey(blogId);
        // 执行lua脚本：判断是否点赞，若已点赞返回-1；若未点赞，添加点赞记录，添加临时点赞数据，更新热点blog的点赞数+1
        log.info("DoThumb using lua...");
        long result = redisTemplate.execute(
                RedisScript.of(new ClassPathResource("templates/Thumb.lua"), Long.class),
                List.of(tempThumbKey, userThumbKey, blogKey),
                userId,
                blogId
        );
        // 处理返回值
        if (result == LuaStatusEnum.FAIL.getValue()){
            throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST, Constant.THUMB_DOTHUMB_ALREADY_THUMBED);
        }
        return result == LuaStatusEnum.SUCCESS.getValue();
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 校验参数和用户登录态
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null || doThumbRequest == null || doThumbRequest.getBlogId() == null){
            throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST, Constant.THUMB_DOTHUMB_INVALID_PARAMS);
        }
        // 准备redis的key
        Long userId = loginUser.getId();
        Long blogId = doThumbRequest.getBlogId();
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(RedisKeyUtil.getTimeSlice());
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        String blogKey = RedisKeyUtil.getBlogKey(blogId);
        // 执行lua脚本：判断是否点赞，若未点赞返回-1；若点赞，取消点赞记录，取消临时点赞数据，更新热点blog的点赞数-1
        log.info("UndoThumb using lua...");
        long result = redisTemplate.execute(
                RedisScript.of(new ClassPathResource("templates/Unthumb.lua"), Long.class),
                List.of(tempThumbKey, userThumbKey, blogKey),
                userId,
                blogId
        );
        // 处理返回值
        if (result == LuaStatusEnum.FAIL.getValue()){
            throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST, Constant.THUMB_DOTHUMB_NOT_THUMBED);
        }
        return result == LuaStatusEnum.SUCCESS.getValue();
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
        return redisTemplate.opsForHash().hasKey(RedisKeyUtil.getUserThumbKey(userId), blogId.toString());
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
        List<Object> hashFields = blogIds.stream().map(Object::toString).collect(Collectors.toList());
        return redisTemplate.opsForHash().multiGet(RedisKeyUtil.getUserThumbKey(userId), hashFields);
    }
}




