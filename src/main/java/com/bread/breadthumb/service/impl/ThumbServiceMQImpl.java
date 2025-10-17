package com.bread.breadthumb.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bread.breadthumb.constant.Constant;
import com.bread.breadthumb.exception.BusinessException;
import com.bread.breadthumb.mapper.ThumbMapper;
import com.bread.breadthumb.model.dto.DoThumbRequest;
import com.bread.breadthumb.model.dto.LuaScriptResult;
import com.bread.breadthumb.model.entity.Thumb;
import com.bread.breadthumb.model.entity.User;
import com.bread.breadthumb.model.enums.LuaStatusEnum;
import com.bread.breadthumb.mq.ThumbEvent;
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
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author huang
 * @description 针对表【thumb】的数据库操作Service实现
 * @createDate 2025-10-12 16:02:41
 */
@Service("thumbService") // thumb记录发送到消息队列，通过消息队列同步到数据库
@Slf4j
public class ThumbServiceMQImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private UserService userService;
    @Autowired
    private PulsarTemplate<ThumbEvent> pulsarTemplate;

    // 1.修改lua脚本的返回类型，返回一个list集合{}
    // 2.修改ThumbMQ.lua和UnthumbMQ.lue脚本，添加点赞记录时，hashvalue设置为点赞时间，删除点赞记录时，要将上次点赞的时间返回
    // 3.修改doThumb和undoThumb代码
    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 校验参数和用户登录态
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null || doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST, Constant.THUMB_DOTHUMB_INVALID_PARAMS);
        }
        // 准备redis的key
        Long userId = loginUser.getId();
        Long blogId = doThumbRequest.getBlogId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        String blogKey = RedisKeyUtil.getBlogKey(blogId);
        // 执行lua脚本：判断是否点赞，若已点赞返回-1；若未点赞，添加点赞记录，更新热点blog的点赞数+1
        log.info("DoThumb: Lua add thumb record to redis...");
        List<String> luaKeys = List.of(userThumbKey, blogKey);
        LocalDateTime thumbTime = LocalDateTime.now();
        List<Object> resultList = redisTemplate.execute(
                RedisScript.of(new ClassPathResource("templates/ThumbMQ.lua"), List.class),
                luaKeys, blogId, thumbTime
        );
        // 处理返回值
        LuaScriptResult result = convertLuaResultList(resultList);
        if (result.getStatus() == LuaStatusEnum.FAIL.getValue()) {
            // 用户已经点赞
            throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST, Constant.THUMB_DOTHUMB_ALREADY_THUMBED);
        }
        // 用户点赞记录已存入redis，准备消息发送至消息队列
        ThumbEvent thumbEvent = ThumbEvent.builder()
                .userId(userId)
                .blogId(blogId)
                .type(ThumbEvent.EventType.INCR)
                .eventTime(thumbTime)
                .build();
        // 发送消息，发送失败则回滚redis
        // 若对数据重要性没有太高要求，可以考虑异步发送
        // 直接返回true给用户，但是异步发送失败时回滚redis，导致用户得到的反馈（以为成功）和实际数据（实则因发送失败回滚）不一致
        log.info("DoThumb: Send thumb event to MQ...");
        // 采用Key_Shared订阅模式，严格保证相同 Key 的消息按顺序投递给同一个消费者，确保消息的顺序性
        // Key的格式为 userId-blogId
        pulsarTemplate.newMessage(thumbEvent)
                .withMessageCustomizer(builder -> builder.key(getThumbMessageKey(thumbEvent)))
                .withTopic(Constant.MQ_THUMB_TOPIC)
                .sendAsync()
                .handle((messageId, throwable) -> {
                    if (throwable != null) {
                        log.error("DoThumb: Send thumb event to MQ failed. Rollback redis...", throwable);
                        // 回滚redis，执行UnthumbMQ.lua脚本
                        redisTemplate.execute(RedisScript.of(new ClassPathResource("templates/UnthumbMQ.lua"), List.class), luaKeys, blogId);
                        return null;
                    } else {
                        log.info("DoThumb: Send thumb event to MQ successfully. Message {}...", messageId);
                        return messageId;
                    }
                });
        log.info("DoThumb: return true");
        return true;
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 校验参数和用户登录态
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null || doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST, Constant.THUMB_DOTHUMB_INVALID_PARAMS);
        }
        // 准备redis的key
        Long userId = loginUser.getId();
        Long blogId = doThumbRequest.getBlogId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        String blogKey = RedisKeyUtil.getBlogKey(blogId);
        // 执行lua脚本：判断是否点赞，若未点赞返回-1；若点赞，取消点赞记录，更新热点blog的点赞数-1
        log.info("UndoThumbLua: Lua delete thumb record in redis...");
        List<String> luaKeys = List.of(userThumbKey, blogKey);
        List<Object> resultList = redisTemplate.execute(
                RedisScript.of(new ClassPathResource("templates/UnthumbMQ.lua"), List.class),
                luaKeys, blogId
        );
        LuaScriptResult result = convertLuaResultList(resultList);
        // 处理返回值
        if (result.getStatus() == LuaStatusEnum.FAIL.getValue()) {
            throw new BusinessException(HttpServletResponse.SC_BAD_REQUEST, Constant.THUMB_DOTHUMB_NOT_THUMBED);
        }
        // 准备消息
        ThumbEvent thumbEvent = ThumbEvent.builder()
                .userId(userId)
                .blogId(blogId)
                .type(ThumbEvent.EventType.DECR)
                .eventTime(LocalDateTime.now())
                .build();
        // 发送消息，发送失败则回滚redis
        log.info("UndoThumbLua: Send delete thumb event to MQ...");
        // 采用Key_Shared订阅模式，严格保证相同 Key 的消息按顺序投递给同一个消费者，确保消息的顺序性
        // Key的格式为 userId-blogId
        pulsarTemplate.newMessage(thumbEvent)
                .withMessageCustomizer(builder -> builder.key(getThumbMessageKey(thumbEvent)))
                .withTopic(Constant.MQ_THUMB_TOPIC)
                .sendAsync()
                .handle((messageId, throwable) -> {
                    if (throwable != null) {
                        log.error("UndoThumbLua: Send delete thumb event to MQ failed. Rollback redis...", throwable);
                        // 回滚redis，执行thumbMQ.lua脚本，需要传入最初的点赞时间
                        LocalDateTime thumbTime =  result.getThumbTime();
                        redisTemplate.execute(RedisScript.of(new ClassPathResource("templates/ThumbMQ.lua"), List.class),
                                luaKeys, blogId, thumbTime);
                        return null;
                    } else {
                        log.info("UndoThumbLua: Send delete thumb event to MQ successfully. Message {}...", messageId);
                        return messageId;
                    }
                });
        log.info("UndoThumbLua: return true");
        return true;
    }

    /**
     * 查询redis，判断用户是否已经点赞
     *
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
     *
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

    private String getThumbMessageKey(ThumbEvent thumbEvent) {
        return thumbEvent.getUserId() + "-" + thumbEvent.getBlogId();
    }

    private LuaScriptResult convertLuaResultList(List<Object> luaResultList){
        LuaScriptResult result = new LuaScriptResult();
        result.setStatus(Long.parseLong(luaResultList.getFirst().toString()));
        if (luaResultList.size() > 1){
            LocalDateTime thumbTime = LocalDateTime.parse(luaResultList.get(1).toString(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            result.setThumbTime(thumbTime);
        }
        return result;
    }

}




