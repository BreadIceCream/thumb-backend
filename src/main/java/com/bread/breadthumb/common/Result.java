package com.bread.breadthumb.common;

import lombok.Data;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {

    private int code;
    private T data;
    private String message;

    public Result(int code, String message, T data){
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success(T data){
        return new Result<>(200,"success", data);
    }

    public static <T> Result<T> error(int code, String message){
        return new Result<>(code, message, null);
    }

}
