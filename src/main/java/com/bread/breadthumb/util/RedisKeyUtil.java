package com.bread.breadthumb.util;

import com.bread.breadthumb.constant.Constant;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RedisKeyUtil {

    public static String getUserThumbKey(Long userId) {
        return Constant.REDIS_USER_THUMB_KEY_PREFIX + userId;
    }

    /**
     * 获取临时点赞记录的key
     * @param time 时间片
     */
    public static String getTempThumbKey(String time) {
        return Constant.REDIS_TEMP_THUMB_KEY_PREFIX.formatted(time);
    }

    public static String getBlogKey(Long blogId){
        return Constant.REDIS_BLOG_KEY_PREFIX + blogId;
    }

    public static String getTimeSlice(){
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime localDateTime = LocalDateTime.of(now.getYear(), now.getMonth(), now.getDayOfMonth(), now.getHour(), now.getMinute(), now.getSecond() / 10 * 10);
        return localDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd:HH:mm:ss"));
    }

}
