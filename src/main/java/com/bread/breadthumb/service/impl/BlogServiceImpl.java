package com.bread.breadthumb.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bread.breadthumb.constant.Constant;
import com.bread.breadthumb.model.entity.Blog;
import com.bread.breadthumb.model.entity.Thumb;
import com.bread.breadthumb.model.entity.User;
import com.bread.breadthumb.model.vo.BlogVO;
import com.bread.breadthumb.service.BlogService;
import com.bread.breadthumb.mapper.BlogMapper;
import com.bread.breadthumb.service.ThumbService;
import com.bread.breadthumb.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
* @author huang
* @description 针对表【blog】的数据库操作Service实现
* @createDate 2025-10-12 16:02:41
*/
@Service
@Slf4j
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {

    @Autowired
    private UserService userService;

    @Autowired
    @Lazy
    private ThumbService thumbService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${blog.hot.number}")
    private int number;
    @Value("${blog.hot.expire}")
    private int hotExpire;
    @Value("${blog.ordinary.expire}")
    private int ordinaryExpire;

    @Scheduled(initialDelay = 3000, fixedDelay = 1000 * 60 * 60 * 24)
    public void loadHotBlog(){
        // 每天24点，获取过去一天创建的所有blog，存入redis。redis只缓存近期或热点数据
        // 对于点赞数最高的number条blog，设置过期时间为hotExpire，普通blog的过期时间为ordinaryExpire
        List<Blog> yesterdayBlogs = lambdaQuery().ge(Blog::getCreateTime, LocalDate.now().minusDays(1).atTime(0,0,0))
                .orderByDesc(Blog::getThumbCount).list();
        log.info("Got {} blogs...", yesterdayBlogs.size());
        // 采用hash结构，key为blog:blogId，field为字段名，value为字段值
        Map<String, Map<String, Object>> map = yesterdayBlogs.stream().collect(Collectors.toMap(
                blog -> Constant.REDIS_BLOG_KEY_PREFIX + blog.getId(),
                blog -> BeanUtil.beanToMap(blog, false, false)
        ));
        // 使用redisTemplate.execute方法执行批量操作
        redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) throws DataAccessException {
                operations.multi();
                for (int i = 0; i < yesterdayBlogs.size(); i++) {
                    Long blogId = yesterdayBlogs.get(i).getId();
                    String key = Constant.REDIS_BLOG_KEY_PREFIX + blogId;
                    //operations.delete((K) key);
                    operations.opsForHash().putAll((K) key, map.get(key));
                    // 设置过期时间
                    if (i < number){
                        log.info("Blog {} is hot", blogId);
                        operations.expire((K) key, hotExpire + (int)(Math.random()*1000), TimeUnit.SECONDS);
                    }else {
                        log.info("Blog {} is ordinary", blogId);
                        operations.expire((K) key, ordinaryExpire + (int)(Math.random()*1000), TimeUnit.SECONDS);
                    }
                }
                return operations.exec();
            }
        });
        log.info("redis缓存blog成功...");
    }

    @Override
    public BlogVO getBlogVOById(long blogId, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        // 先查询redis, 获取blog
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(Constant.REDIS_BLOG_KEY_PREFIX + blogId);
        BlogVO blogVO = new BlogVO();
        if (!entries.isEmpty()){
            log.info("redis命中blog {}...", blogId);
            // 当前blog是近期或热点数据
            BeanUtil.fillBeanWithMap(entries, blogVO, false);
        }else {
            // 缓存未命中，从数据库中查询blog
            Blog blog = getById(blogId);
            BeanUtil.copyProperties(blog, blogVO);
        }
        Boolean thumbed = thumbService.hasThumbRedis(blogId, loginUser.getId());
        blogVO.setHasThumb(thumbed);
        return blogVO;
    }


    @Override
    public List<BlogVO> getBlogVOList(HttpServletRequest request) {
        List<Blog> blogList = list();
        User loginUser = userService.getLoginUser(request);
        // 当前用户点赞过的blogId
        Set<Long> thumbedBlogIdSet = new HashSet<>();
        if (loginUser != null) {
            // 获取当前用户在blogList中点赞过的blog
            List<Long> blogIds = blogList.stream().map(Blog::getId).toList();
            List<Object> thumbIds = thumbService.hasThumbRedis(blogIds, loginUser.getId());
            // 返回的thumbIds和blogIds是一一对应的
            for (int i = 0; i < thumbIds.size(); i++) {
                if (thumbIds.get(i) != null){
                    thumbedBlogIdSet.add(blogIds.get(i));
                }
            }
        }
        // 将blogList中的Blog转为BlogVO，同时设置当前用户点赞过的Blog
        return blogList.stream().map(blog -> {
            BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
            blogVO.setHasThumb(thumbedBlogIdSet.contains(blog.getId()));
            return blogVO;
        }).toList();
    }


}




