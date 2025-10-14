package com.bread.breadthumb.service;

import com.bread.breadthumb.model.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import com.bread.breadthumb.model.vo.BlogVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author huang
* @description 针对表【blog】的数据库操作Service
* @createDate 2025-10-12 16:02:41
*/
public interface BlogService extends IService<Blog> {

    /**
     * 获取博客详情
     * @param blogId
     * @param request
     * @return
     */
    BlogVO getBlogVOById(long blogId, HttpServletRequest request);

    /**
     * 获取所有博客列表
     * @param request
     * @return
     */
    List<BlogVO> getBlogVOList(HttpServletRequest request);


}
