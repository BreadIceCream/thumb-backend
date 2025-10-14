package com.bread.breadthumb;

import com.bread.breadthumb.constant.Constant;
import com.bread.breadthumb.util.RedisKeyUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class Test {

    public static void main(String[] args) {
        String yesterday = LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String keysPattern = RedisKeyUtil.getTempThumbKey(yesterday) + ":*";
        System.out.println(keysPattern);
    }

}
