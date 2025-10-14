package com.bread.breadthumb.job;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bread.breadthumb.mapper.BlogMapper;
import com.bread.breadthumb.model.entity.Thumb;
import com.bread.breadthumb.model.enums.ThumbTypeEnum;
import com.bread.breadthumb.service.ThumbService;
import com.bread.breadthumb.util.RedisKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时同步点赞数据到数据库
 */
@Component
@Slf4j
public class SyncThumb2DBJob {

    // 临时点赞记录的是某一段时间段内，用户的点赞/取消点赞操作对数据库中的thumbCount的影响。判断用户是否点赞是通过另外一个redisKey进行的。
    // 例如，在10:00-10:10之间，user1点赞了blog1，则在这个临时点赞记录中，会有一条数据hashKey1:1 value1。若在此期间取消了点赞，则hashKey1:1 value0。最终不会影响数据库中的thumbCount。
    // 但若在当前时间段user1没有取消点赞，则value1会被写入数据库，thumbCount+1。在下一个时间段10:10-10:20，user1取消点赞，会新添一条数据hashKey1:1 value-1，表示点赞数-1。当该时间段的数据写入数据库时，数据库中的thumbCount-1。

    @Autowired
    private ThumbService thumbService;

    @Autowired
    private BlogMapper blogMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // 每10秒执行一次
    @Scheduled(fixedRate = 10000)
    public void run(){
        // 获取上一个10秒时间段
        LocalDateTime prev = LocalDateTime.now().minusSeconds(10);
        LocalDateTime localDateTime = LocalDateTime.of(prev.getYear(), prev.getMonth(), prev.getDayOfMonth(), prev.getHour(), prev.getMinute(), prev.getSecond() / 10 * 10);
        String prevTimeString = localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd:HH:mm:ss"));
        log.info("Scheduled Task: SyncThumb2DB start, time piece {} ...", prevTimeString);
        SyncThumb2DBJob proxy = (SyncThumb2DBJob) AopContext.currentProxy();
        proxy.syncThumb2DBByTime(prevTimeString);
        log.info("Scheduled Task: SyncThumb2DB end...");
    }

    /**
     * @param time 要处理的时间片
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncThumb2DBByTime(String time){
        // 获取临时点赞记录数据。tempThumbKey为 thumb:temp:2025-10-14:10:11:20
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(time);
        syncThumb2DBByTimeOfferKey(tempThumbKey);
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncThumb2DBByTimeOfferKey(String tempThumbKey){
        Map<Object, Object> allTempThumbMap = redisTemplate.opsForHash().entries(tempThumbKey);
        if (CollUtil.isEmpty(allTempThumbMap)){
            log.info("SyncThumb2DB no data...");
            return;
        }
        Map<Long, Long> blogIdThumbCountMap = new HashMap<>();
        List<Thumb> thumbList = new ArrayList<>();
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        boolean needDelete = false;
        for (Object userIdBlogIdObj : allTempThumbMap.keySet()) {
            String userIdBlogId = (String) userIdBlogIdObj;
            String[] split = userIdBlogId.split(":");
            Long userId = Long.parseLong(split[0]);
            Long blogId = Long.parseLong(split[1]);
            // 获取当前点赞的类型，+1点赞/-1取消点赞/0不影响
            Integer thumbType = Integer.valueOf(allTempThumbMap.get(userIdBlogId).toString());
            if (thumbType == ThumbTypeEnum.INCR.getValue()){
                // 点赞，创建Thumb对象
                Thumb thumb = new Thumb();
                thumb.setUserId(userId);
                thumb.setBlogId(blogId);
                thumbList.add(thumb);
            }else if (thumbType == ThumbTypeEnum.DECR.getValue()){
                // 取消点赞，需要删除Thumb表的记录。wrapper拼接条件
                needDelete = true;
                wrapper.or().eq(Thumb::getUserId, userId).eq(Thumb::getBlogId, blogId);
            }else {
                // 不影响
                if (thumbType != ThumbTypeEnum.NON.getValue()){
                    log.error("SyncThumb2DB data error, userId {}, blogId {}, thumbType {}", userId, blogId, thumbType);
                }
                continue;
            }
            // 更新当前blog的点赞数
            blogIdThumbCountMap.put(blogId, blogIdThumbCountMap.getOrDefault(blogId, 0L) + thumbType);
        }
        // 使用虚拟线程对Thumb表、Blog表、redis进行操作（IO密集型）
        // Thumb表：批量保存新点赞记录，批量删除取消点赞的记录
        boolean finalNeedDelete = needDelete;
        if (!thumbList.isEmpty()){
            log.info("SyncThumb2DB save thumbList...");
            thumbService.saveBatch(thumbList);
        }
        if (finalNeedDelete){
            log.info("SyncThumb2DB delete thumbs...");
            thumbService.remove(wrapper);
        }
        // Blog表：批量更新blog的点赞数
        if (!blogIdThumbCountMap.isEmpty()){
            log.info("SyncThumb2DB update blogThumbCount...");
            blogMapper.batchUpdateThumbCount(blogIdThumbCountMap);
        }
        // 删除redis中的临时记录
        Thread.startVirtualThread(() -> redisTemplate.delete(tempThumbKey));
    }

}
