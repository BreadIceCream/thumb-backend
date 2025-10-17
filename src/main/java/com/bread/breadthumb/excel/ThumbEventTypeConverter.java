package com.bread.breadthumb.excel;

import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.GlobalConfiguration;
import com.alibaba.excel.metadata.data.ReadCellData;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import com.bread.breadthumb.constant.Constant;
import com.bread.breadthumb.mq.ThumbEvent;

public class ThumbEventTypeConverter implements Converter<ThumbEvent.EventType> {

    public static final ThumbEventTypeConverter INSTANCE = new ThumbEventTypeConverter();

    // 声明该转换器支持的 Excel 单元格类型：字符串
    @Override
    public CellDataTypeEnum supportExcelTypeKey() {
        return CellDataTypeEnum.STRING;
    }

    // 标识该转换器支持的 Java 类型（Class<T>）。
    @Override
    public Class<?> supportJavaTypeKey() {
        return ThumbEvent.EventType.class;
    }

    // 读取 Excel (从 Excel 单元格 -> Java 对象)
    @Override
    public ThumbEvent.EventType convertToJavaData(ReadCellData<?> cellData, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) throws Exception {
        String value = cellData.getStringValue();
        return switch (value) {
            case Constant.EXCEL_THUMB_TYPE_INCR -> ThumbEvent.EventType.INCR;
            case Constant.EXCEL_THUMB_TYPE_DECR -> ThumbEvent.EventType.DECR;
            default -> null;
        };
    }

    // 写入 Excel (从 Java 对象 -> Excel 单元格)
    @Override
    public WriteCellData<?> convertToExcelData(ThumbEvent.EventType value, ExcelContentProperty contentProperty, GlobalConfiguration globalConfiguration) throws Exception {
        if (value == ThumbEvent.EventType.INCR){
            return new WriteCellData<>(Constant.EXCEL_THUMB_TYPE_INCR);
        }else if (value == ThumbEvent.EventType.DECR){
            return new WriteCellData<>(Constant.EXCEL_THUMB_TYPE_DECR);
        }else {
            return new WriteCellData<>(Constant.EXCEL_UNKNOWN_TYPE);
        }
    }

}
