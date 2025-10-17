package com.bread.breadthumb.config;

import com.bread.breadthumb.constant.Constant;
import org.apache.pulsar.client.api.BatchReceivePolicy;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.DeadLetterPolicy;
import org.apache.pulsar.client.api.RedeliveryBackoff;
import org.apache.pulsar.client.impl.MultiplierRedeliveryBackoff;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.pulsar.annotation.PulsarListenerConsumerBuilderCustomizer;

import java.util.concurrent.TimeUnit;

@Configuration
public class ThumbConsumerConfig {

    @Bean
    public PulsarListenerConsumerBuilderCustomizer thumbConsumerCustomizer(){
        return consumerBuilder -> {
            // 配置批量处理消息
            consumerBuilder.batchReceivePolicy(BatchReceivePolicy.builder()
                    // 每次处理1000条消息
                    .maxNumMessages(1000)
                    .timeout(10_000, TimeUnit.MILLISECONDS)
                    .build());
            // 配置NACK重试退避机制
            consumerBuilder.negativeAckRedeliveryBackoff(negativeAckRedeliveryBackoff());
            // 配置ACK超时重试退避机制
            consumerBuilder.ackTimeoutRedeliveryBackoff(ackTimeoutRedeliveryBackoff());
            // 配置死信队列
            consumerBuilder.deadLetterPolicy(deadLetterPolicy());
        };
    }


    // 配置NACK重试退避机制
    public RedeliveryBackoff negativeAckRedeliveryBackoff(){
        return MultiplierRedeliveryBackoff.builder()
                .minDelayMs(1000)
                .maxDelayMs(60_000)
                .multiplier(2)
                .build();
    }

    // 配置ACK超时重试退避机制
    public RedeliveryBackoff ackTimeoutRedeliveryBackoff(){
        return MultiplierRedeliveryBackoff.builder()
                .minDelayMs(5000)
                .maxDelayMs(300_000)
                .multiplier(3)
                .build();
    }

    public DeadLetterPolicy deadLetterPolicy(){
        return DeadLetterPolicy.builder()
                // 最大重试次数为3
                .maxRedeliverCount(3)
                // 死信主题
                .deadLetterTopic(Constant.MQ_DEAD_LETTER_TOPIC)
                .build();
    }


}
