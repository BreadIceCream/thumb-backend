package com.bread.breadthumb.controller;

import com.bread.breadthumb.common.Result;
import com.bread.breadthumb.model.dto.DoThumbRequest;
import com.bread.breadthumb.service.ThumbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/thumb")
@Tag(name = "点赞接口")
public class ThumbController {

    @Autowired
    private ThumbService thumbService;

    @PostMapping("/do")
    @Operation(summary = "点赞")
    public Result<Boolean> doThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request){
        Boolean result = thumbService.doThumb(doThumbRequest, request);
        return Result.success(result);
    }

    @PostMapping("/undo")
    @Operation(summary = "取消点赞")
    public Result<Boolean> undoThumb(@RequestBody DoThumbRequest doThumbRequest, HttpServletRequest request){
        Boolean result = thumbService.undoThumb(doThumbRequest, request);
        return Result.success(result);
    }

}
