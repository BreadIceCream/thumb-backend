package com.bread.breadthumb.service;

import com.bread.breadthumb.model.dto.DoThumbRequest;
import com.bread.breadthumb.model.entity.Thumb;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author huang
* @description 针对表【thumb】的数据库操作Service
* @createDate 2025-10-12 16:02:41
*/
public interface ThumbService extends IService<Thumb> {

    /**
     * 点赞
     * @param doThumbRequest
     * @param request
     * @return
     */
    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    /**
     * 取消点赞
     * @param doThumbRequest
     * @param request
     * @return
     */
    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    /**
     * 判断用户是否已经点赞
     * @param blogId
     * @param userId
     * @return
     */
    Boolean hasThumbRedis(Long blogId, Long userId);

    /**
     * 判断用户是否已经点赞，接受blogId列表
     * @param blogIds
     * @param userId
     * @return ThumbId列表
     */
    List<Object> hasThumbRedis(List<Long> blogIds, Long userId);
}
