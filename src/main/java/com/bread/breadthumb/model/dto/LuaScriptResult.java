package com.bread.breadthumb.model.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LuaScriptResult {

    private long status;
    private LocalDateTime thumbTime;

}
