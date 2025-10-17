package com.bread.breadthumb.mapper;

import com.bread.breadthumb.model.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
* @author huang
* @description 针对表【blog】的数据库操作Mapper
* @createDate 2025-10-12 16:02:41
* @Entity com.bread.breadthumb.model.entity.Blog
*/
@Mapper
public interface BlogMapper extends BaseMapper<Blog> {

    /**
     * 批量更新点赞数
     * @param countMap key为blogId，value为需要更新的数
     */
    void batchUpdateThumbCount(@Param("countMap")Map<Long, Long> countMap);

}




