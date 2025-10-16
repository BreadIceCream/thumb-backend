package com.bread.breadthumb.util;

import cn.hutool.core.bean.BeanUtil;
import com.bread.breadthumb.common.HeavyKeeper;
import com.bread.breadthumb.constant.Constant;
import com.bread.breadthumb.model.entity.Blog;
import com.bread.breadthumb.service.BlogService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@Slf4j
public class BlogCacheManager {

    // hotBlogDetector实时监测搜索和点赞Top K的blog，使用blogId作为Item的key。
    // blogLocalCache本地缓存此刻属于Top K的blog
    private HeavyKeeper hotBlogDetector = new HeavyKeeper(10000, 10, 100, 1.08);
    private Cache<String, Blog> blogLocalCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private BlogService blogService;

    // 使用定时任务，每5分钟向redis中写入这个时刻Top K的blog，设置过期时间为10分钟
    @Scheduled(initialDelay = 1000 * 10, fixedRate = 1000 * 60 * 5)
    public void syncHotBlog2Redis(){
        log.info("Scheduled Task: load HotBlog to redis start...");
        // 准备写入的Top K Blog
        List<HeavyKeeper.Item> topK = hotBlogDetector.getTopK();
        List<Long> blogIds = topK.stream().map(item -> Long.parseLong(item.getKey())).toList();
        List<Blog> blogList = blogService.lambdaQuery().in(Blog::getId, blogIds).list();
        log.info("Scheduled Task: Got {} Hot Blogs...", blogList.size());
        // 采用hash结构，key为blog:blogId，field为字段名，value为字段值
        Map<String, Map<String, Object>> map = blogList.stream().collect(Collectors.toMap(
                blog -> RedisKeyUtil.getBlogKey(blog.getId()),
                blog -> BeanUtil.beanToMap(blog, false, false)
        ));
        // 使用redisTemplate.execute执行事务保证操作的原子性
        redisTemplate.execute(new SessionCallback<List<Object>>() {
            @Override
            public <K, V> List<Object> execute(RedisOperations<K, V> operations) throws DataAccessException {
                operations.multi();
                for (Map.Entry<String, Map<String, Object>> blogRedisKeyMapEntry : map.entrySet()) {
                    String key = blogRedisKeyMapEntry.getKey();
                    Map<String, Object> map = blogRedisKeyMapEntry.getValue();
                    operations.opsForHash().putAll((K) key, map);
                    // 设置过期时间为10分钟左右
                    operations.expire((K) key, 60 * 10 + ThreadLocalRandom.current().nextInt(120), TimeUnit.SECONDS);
                }
                return operations.exec();
            }
        });
        log.info("Scheduled Task: load HotBlog to redis successfully...");
    }

    public Blog getBlog(Long blogId){
        String cacheKey = buildCacheKey(Constant.CACHE_BLOG_KEY_PREFIX, blogId.toString());
        // 该blog访问次数+1
        boolean isHotBlog = hotBlogDetector.add(blogId.toString());
        // 从本地缓存中获取
        Blog blog = blogLocalCache.getIfPresent(cacheKey);
        if (blog != null){
            log.info("localCache命中{}...", cacheKey);
            return blog;
        }
        // 本地缓存未命中，从redis中获取。redis中保留了近10分钟的热点blog
        String redisBlogKey = RedisKeyUtil.getBlogKey(blogId);
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(redisBlogKey);
        if (!entries.isEmpty()){
            log.info("redis命中{}...", cacheKey);
            blog = new Blog();
            BeanUtil.fillBeanWithMap(entries, blog, false);
            if (isHotBlog){
                // 当前blog是此刻的热点blog，放入本地缓存
                log.info("{} 放入本地缓存...", cacheKey);
                blogLocalCache.put(cacheKey, blog);
            }
            return blog;
        }
        // redis也未命中，说明这个blog并没有在前两个redis写入时间点作为Top K写入
        // 从数据库中获取。判断这个blog此刻是否为Top K，若是，异步写入本地缓存和redis
        Blog currentBlog = blogService.getById(blogId);
        if (isHotBlog && currentBlog != null){
            log.info("{} 此刻为Hot Blog，异步写入本地缓存和redis...", cacheKey);
            Thread.startVirtualThread(() -> {
                blogLocalCache.put(cacheKey, currentBlog);
                Map<String, Object> map = BeanUtil.beanToMap(currentBlog, false, false);
                redisTemplate.opsForHash().putAll(redisBlogKey, map);
                redisTemplate.expire(redisBlogKey, 60 * 10 + ThreadLocalRandom.current().nextInt(120), TimeUnit.SECONDS);
            });
        }
        return currentBlog;
    }

    private String buildCacheKey(String keyPrefix, String key){
        return keyPrefix + ":" + key;
    }

}
