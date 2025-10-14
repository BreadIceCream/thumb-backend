package com.bread.breadthumb.controller;

import com.bread.breadthumb.common.Result;
import com.bread.breadthumb.model.entity.Blog;
import com.bread.breadthumb.model.vo.BlogVO;
import com.bread.breadthumb.service.BlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/blog")
@Tag(name = "博客接口")
public class BlogController {

    @Autowired
    private BlogService blogService;

    @GetMapping("/get")
    @Operation(summary = "获取博客")
    public Result<BlogVO> get(Long blogId, HttpServletRequest request){
        return Result.success(blogService.getBlogVOById(blogId, request));
    }

    @GetMapping("/list")
    @Operation(summary = "获取博客列表")
    public Result<List<BlogVO>> list(HttpServletRequest request){
        return Result.success(blogService.getBlogVOList(request));
    }



}
