package com.bread.breadthumb.mq;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThumbEvent implements Serializable {

    @ExcelProperty("user ID")
    private Long userId;
    @ExcelProperty("blog ID")
    private Long blogId;
    @ExcelProperty("thumb type")
    private EventType type;
    @ExcelProperty("thumb time")
    private LocalDateTime eventTime;

    public enum EventType {
        // 点赞，thumbCount+1
        INCR,
        // 取消点赞，thumbCount-1
        DECR
    }

}
