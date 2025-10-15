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
}
