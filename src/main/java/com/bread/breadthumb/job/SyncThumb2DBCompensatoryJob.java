package com.bread.breadthumb.job;

import cn.hutool.core.collection.CollUtil;
import com.bread.breadthumb.util.RedisKeyUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@Component
@Slf4j
public class SyncThumb2DBCompensatoryJob {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SyncThumb2DBJob syncThumb2DBJob;

    @Scheduled(cron = "0 30 0 * * ?")
    public void run(){
        log.info("Scheduled Task: SyncThumb2DB compensatory job start...");
        // 每天0点30进行检查，获取redis中昨天所有的临时点赞记录。tempThumbKey为 thumb:temp:2025-10-14:10:11:20
        String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String keysPattern = RedisKeyUtil.getTempThumbKey(yesterday) + ":*";
        Set<String> tempThumbKeys = redisTemplate.keys(keysPattern);
        if (CollUtil.isEmpty(tempThumbKeys)){
            log.info("Scheduled Task: SyncThumb2DB compensatory job no data...");
            return;
        }
        // 处理数据
        for (String tempThumbKey : tempThumbKeys) {
            syncThumb2DBJob.syncThumb2DBByTimeOfferKey(tempThumbKey);
        }
        log.info("Scheduled Task: SyncThumb2DB compensatory job end...");
    }

}
