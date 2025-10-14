package com.bread.breadthumb.mapper;

import com.bread.breadthumb.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author huang
* @description 针对表【user】的数据库操作Mapper
* @createDate 2025-10-12 16:02:41
* @Entity com.bread.breadthumb.model.entity.User
*/
@Mapper
public interface UserMapper extends BaseMapper<User> {

}




