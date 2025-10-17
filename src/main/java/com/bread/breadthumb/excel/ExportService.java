package com.bread.breadthumb.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import com.alibaba.excel.write.metadata.WriteSheet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Component
@Slf4j
public class ExportService {

    // 程序运行时，记录下一次要写入的行数
    public void exportData(String filePath, String sheetName, List<?> dataList, Class<?> headClazz) {
        File originalFile = new File(filePath);
        File tempFile = null;
        CustomizeRowWriteHandler rowWriteHandler = new CustomizeRowWriteHandler();
        ExcelWriterBuilder writerBuilder = EasyExcel.write()
                .head(headClazz)
                .registerWriteHandler(rowWriteHandler)
                .registerConverter(ThumbEventTypeConverter.INSTANCE);
        try {
            if (originalFile.exists()){
                // 追加数据。创建临时文件，将file中的原始数据和新数据写入临时文件，再重命名
                log.info("Export data to Excel: Append data size {}...", dataList.size());
                tempFile = new File(originalFile.getParentFile() + File.separator + "temp.xlsx");
                writerBuilder.withTemplate(originalFile).file(tempFile).needHead(false);
            }else {
                log.info("Export data to Excel: Create new file {}. Add data size {}...", filePath, dataList.size());
                originalFile.createNewFile();
                writerBuilder.file(originalFile).needHead(true);
            }
        } catch (IOException e) {
            log.error("Export data to Excel: create file error...", e);
            return;
        }
        try (ExcelWriter excelWriter = writerBuilder.build()){
            WriteSheet writeSheet = EasyExcel.writerSheet(sheetName).build();
            excelWriter.write(dataList, writeSheet);
            int completedRowCount = rowWriteHandler.getCompletedRowCount();
            excelWriter.finish();
            log.info("Export data to Excel: originalFile {}, sheet {}, original data size {}, successfully written data {}",
                    filePath, sheetName, dataList.size(), tempFile == null ? completedRowCount - 1 : completedRowCount);
        }catch (Exception e){
            int completedRowCount = rowWriteHandler.getCompletedRowCount();
            log.error("Export data to Excel: ",  e);
            log.error("Export data to Excel: originalFile {}, sheet {}, original data size {}, successfully written data {}",
                    filePath, sheetName, dataList.size(), tempFile == null ? completedRowCount - 1 : completedRowCount);
        }finally {
            // 如果创建了临时文件，将临时文件重命名为 originalFile，删除原始的文件
            if (tempFile != null && tempFile.exists()){
                originalFile.delete();
                tempFile.renameTo(originalFile);
            }
        }
    }

}
