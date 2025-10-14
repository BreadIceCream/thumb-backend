package com.bread.breadthumb.service;

import com.bread.breadthumb.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author huang
* @description 针对表【user】的数据库操作Service
* @createDate 2025-10-12 16:02:41
*/
public interface UserService extends IService<User> {

    /**
     * 获取当前登录用户
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);
}
