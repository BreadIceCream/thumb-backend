package com.bread.breadthumb.excel;

import com.alibaba.excel.write.handler.context.RowWriteHandlerContext;
import com.alibaba.excel.write.handler.impl.DefaultRowWriteHandler;

import java.util.concurrent.atomic.AtomicInteger;

public class CustomizeRowWriteHandler extends DefaultRowWriteHandler {

    private AtomicInteger completedRowCount = new AtomicInteger(0);
    private AtomicInteger lastRowIndex = new AtomicInteger(0);

    @Override
    public void afterRowDispose(RowWriteHandlerContext context) {
        super.afterRowDispose(context);
        lastRowIndex.set(context.getWriteSheetHolder().getLastRowIndex());
        completedRowCount.incrementAndGet();
    }

    public int getCompletedRowCount() {
        return completedRowCount.get();
    }

    public int getLastRowIndex() {
        return lastRowIndex.get();
    }

}
