package com.bread.breadthumb.constant;

public class Constant {

    public static final String LOGIN_USER = "user";
    public static final String THUMB_DOTHUMB_INVALID_PARAMS = "无法点赞";
    public static final String THUMB_DOTHUMB_ALREADY_THUMBED = "用户已经点赞";
    public static final String THUMB_DOTHUMB_NOT_THUMBED = "用户没有点赞";
    public static final String REDIS_USER_THUMB_KEY_PREFIX = "thumb:";

    public static final String REDIS_BLOG_KEY_PREFIX = "blog:";
    public static final long REDIS_HOT_BLOG_EXPIRE_TIME = 30L;

    public static final String REDIS_TEMP_THUMB_KEY_PREFIX = "thumb:temp:%s";
    public static final String CACHE_BLOG_KEY_PREFIX = "cache:blog:";
    public static final String BLOG_NOT_FOUND = "该博客不存在";
    public static final String MQ_THUMB_TOPIC = "thumb-topic";
    public static final String MQ_DEAD_LETTER_TOPIC = "thumb-dlq-topic";
    public static final String MQ_THUMB_SUBSCRIPTION = "thumb-subscription";
    public static final String MQ_CONSUMER_CONCURRENCY = "3";
    public static final String EXCEL_UNKNOWN_TYPE = "未知类型";
    public static final String EXCEL_THUMB_TYPE_INCR = "点赞 INCR";
    public static final String EXCEL_THUMB_TYPE_DECR = "取消点赞 DECR";
    public static final String MQ_DLQ_EXCEL_FILE_PATH_PREFIX = "src/main/resources/static/";
}
