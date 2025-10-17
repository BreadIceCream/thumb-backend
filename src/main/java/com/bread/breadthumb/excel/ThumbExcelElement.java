package com.bread.breadthumb.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.bread.breadthumb.mq.ThumbEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ThumbExcelElement {

    @ExcelProperty("message ID")
    private String messageId;
    @ExcelProperty("user ID")
    private Long userId;
    @ExcelProperty("blog ID")
    private Long blogId;
    @ExcelProperty(value = "thumb type", converter = ThumbEventTypeConverter.class)
    private ThumbEvent.EventType type;
    @ExcelProperty("thumb time")
    private LocalDateTime eventTime;

}
