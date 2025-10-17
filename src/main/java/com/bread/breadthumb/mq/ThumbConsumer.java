package com.bread.breadthumb.mq;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bread.breadthumb.constant.Constant;
import com.bread.breadthumb.excel.ExportService;
import com.bread.breadthumb.excel.ThumbExcelElement;
import com.bread.breadthumb.mapper.BlogMapper;
import com.bread.breadthumb.model.entity.Thumb;
import com.bread.breadthumb.service.ThumbService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.common.schema.SchemaType;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ThumbConsumer {

    private final BlogMapper blogMapper;
    private final ThumbService thumbService;
    private final ExportService exportService;

    @PulsarListener(
            topics = Constant.MQ_THUMB_TOPIC,
            subscriptionName = Constant.MQ_THUMB_SUBSCRIPTION,
            subscriptionType = SubscriptionType.Key_Shared,
            concurrency = Constant.MQ_CONSUMER_CONCURRENCY,
            schemaType = SchemaType.JSON,
            batch = true,
            consumerCustomizer = "thumbConsumerCustomizer"
    )
    @Transactional(rollbackFor = Exception.class)
    public void consumeMessageBatch(List<Message<ThumbEvent>> messages) {
        String consumerName = Thread.currentThread().getName();
        log.info("ThumbConsumer {}: processBatch {}...", consumerName, messages.size());
        // 过滤无效消息，按照key（userId-blogId）进行分组，分组后收集成ThumbEvent列表并按照时间排序，取最后一个事件作为等效最终事件
        // key是userId-blogId，value是一个ThumbEvent，表示userId对blogId一系列点赞/取消点赞行为的最终效果
        Map<String, ThumbEvent> lastEventMap = messages.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        // 按照key分组
                        Message::getKey, Collectors.mapping(
                                // 组中对每个Message进行映射，获取ThumbEvent
                                Message::getValue, Collectors.filtering(
                                        // 对每个ThumbEvent进行过滤并收集
                                        Objects::nonNull, Collectors.collectingAndThen(
                                                Collectors.toList(), list -> {
                                                    // 如果集合长度为偶数，则表示点赞/取消点赞行为抵消，返回null
                                                    if (list.size() % 2 == 0) {
                                                        return null;
                                                    }
                                                    // 按照时间升序排序，取最后一个Event作为最终事件
                                                    list.sort(Comparator.comparing(ThumbEvent::getEventTime));
                                                    return list.getLast();
                                                }
                                        )
                                )
                        )
                ));
        // thumbList记录需要插入数据库的Thumb，deleteWrapper记录需要删除的Thumb，countMap记录每个blogId的点赞数变化量.
        // 使用ConcurrentHashMap保证map单个操作的并发安全性
        List<Thumb> thumbList = new ArrayList<>();
        AtomicBoolean needDelete = new AtomicBoolean(false);
        LambdaQueryWrapper<Thumb> deleteWrapper = new LambdaQueryWrapper<>();
        Map<Long, Long> countMap = new ConcurrentHashMap<>();
        // 对每个最终事件进行处理
        lastEventMap.forEach((userIdBlogId, finalEvent) -> {
            if (finalEvent == null){
                // 点赞/取消点赞行为抵消，忽略
                log.info("ThumbConsumer {}: {} 点赞行为抵消，忽略...", consumerName, userIdBlogId);
                return;
            }
            ThumbEvent.EventType finalAction = finalEvent.getType();
            Long userId = finalEvent.getUserId();
            Long blogId = finalEvent.getBlogId();
            if (finalAction == ThumbEvent.EventType.INCR){
                log.info("ThumbConsumer {}: {} 点赞...", consumerName, userIdBlogId);
                Thumb thumb = new Thumb();
                thumb.setUserId(userId);
                thumb.setBlogId(blogId);
                thumb.setCreateTime(finalEvent.getEventTime());
                thumbList.add(thumb);
                countMap.merge(blogId, 1L, Long::sum);
            }else {
                log.info("ThumbConsumer {}: {} 取消点赞...", consumerName, userIdBlogId);
                needDelete.set(true);
                deleteWrapper.or().eq(Thumb::getUserId, userId).eq(Thumb::getBlogId, blogId);
                countMap.merge(blogId, -1L, Long::sum);
            }
        });
        // 批量更新数据库
        if (needDelete.get()){
            thumbService.remove(deleteWrapper);
        }
        batchUpdateBlogs(countMap);
        batchUpdateThumbs(thumbList);
        log.info("ThumbConsumer {}: processBatch {} done.", consumerName, messages.size());
    }

    @PulsarListener(topics = Constant.MQ_DEAD_LETTER_TOPIC)
    public void consumeDlq(Message<ThumbEvent> message){
        ThumbExcelElement excelElement = BeanUtil.copyProperties(message.getValue(), ThumbExcelElement.class);
        excelElement.setMessageId(message.getMessageId().toString());
        // 将死信消息写入excel文件
        exportService.exportData(Constant.MQ_DLQ_EXCEL_FILE_PATH_PREFIX + LocalDate.now() + ".xlsx",
                "Dead Letters", List.of(excelElement), ThumbExcelElement.class);
    }

    public void batchUpdateBlogs(Map<Long, Long> countMap){
        if (!countMap.isEmpty()){
            blogMapper.batchUpdateThumbCount(countMap);
        }
    }

    public void batchUpdateThumbs(List<Thumb> thumbList){
        if (!thumbList.isEmpty()){
            // 分批次插入
            thumbService.saveBatch(thumbList, 500);
        }
    }

}
