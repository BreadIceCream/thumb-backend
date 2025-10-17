package com.bread.breadthumb;

import cn.hutool.core.date.DateTime;
import com.alibaba.excel.annotation.ExcelProperty;
import com.bread.breadthumb.excel.ExportService;
import com.bread.breadthumb.excel.ThumbExcelElement;
import com.bread.breadthumb.mq.ThumbEvent;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.format.DateTimeFormatters;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@SpringBootTest
class BreadThumbApplicationTests {

    @Autowired
    private ExportService exportService;

    @Test
    void dlqToExcel() {
        String filePath = "src/main/resources/static/dlq-test.xlsx";
        Random rand = new Random();
        List<ThumbExcelElement> dataList = new ArrayList<>();
        for (int i = 1; i <= 100; i++){
            ThumbExcelElement element = new ThumbExcelElement("msg-" + i, (long) rand.nextInt(1, 21), (long) rand.nextInt(1, 501), i % 2 == 0 ? ThumbEvent.EventType.INCR : ThumbEvent.EventType.DECR, LocalDateTime.now());
            dataList.add(element);
        }
        exportService.exportData(filePath, "test", dataList, ThumbExcelElement.class);
    }

    @Test
    void toExcel(){
        String filePath = "src/main/resources/static/other.xlsx";
        Random rand = new Random();
        List<User> dataList = new ArrayList<>();
        for (int i = 1; i <= 100; i++){
            User user = new User();
            user.setUsername("username-" + i);
            user.setPassword("password-" + i);
            dataList.add(user);
        }
        exportService.exportData(filePath, "test", dataList, User.class);
    }

    @Data
    class User {
        @ExcelProperty("用户名")
        private String username;
        @ExcelProperty("密码")
        private String password;
    }

}
