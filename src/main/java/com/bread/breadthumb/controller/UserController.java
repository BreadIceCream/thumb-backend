package com.bread.breadthumb.controller;

import com.bread.breadthumb.common.Result;
import com.bread.breadthumb.constant.Constant;
import com.bread.breadthumb.model.entity.User;
import com.bread.breadthumb.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author huang
 */
@RestController
@RequestMapping("/user")
@Tag(name = "用户接口")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/login")
    @Operation(summary = "用户登录")
    public Result<User> login(Long userId, HttpServletRequest request){
        User user = userService.getById(userId);
        request.getSession().setAttribute(Constant.LOGIN_USER, user);
        return Result.success(user);
    }

    @GetMapping("/get/login")
    @Operation(summary = "获取当前登录用户")
    public Result<User> getLoginUser(HttpServletRequest request){
        User user = userService.getLoginUser(request);
        return Result.success(user);
    }

}
