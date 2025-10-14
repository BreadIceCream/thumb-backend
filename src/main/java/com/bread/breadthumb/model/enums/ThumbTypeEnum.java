package com.bread.breadthumb.model.enums;

import lombok.Getter;

/**
 * 点赞类型枚举
 */
@Getter
public enum ThumbTypeEnum {

    INCR(1),
    DECR(-1),
    NON(0);

    private final int value;

    ThumbTypeEnum(int value) {
        this.value = value;
    }


}
