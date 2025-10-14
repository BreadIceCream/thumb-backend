package com.bread.breadthumb.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.bread.breadthumb.constant.Constant;
import com.bread.breadthumb.model.entity.User;
import com.bread.breadthumb.service.UserService;
import com.bread.breadthumb.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
* @author huang
* @description 针对表【user】的数据库操作Service实现
* @createDate 2025-10-12 16:02:41
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService{

    @Override
    public User getLoginUser(HttpServletRequest request) {
        return (User) request.getSession().getAttribute(Constant.LOGIN_USER);
    }
}




