package com.bread.breadthumb.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;

/**
 * 
 * @TableName blog
 */
@TableName(value ="blog")
@Data
@Schema(description = "blog")
public class Blog implements Serializable {
    /**
     * 
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "id")
    private Long id;

    /**
     * 所属用户id
     */
    @TableField(value = "userId")
    @Schema(description = "所属用户id")
    private Long userId;

    /**
     * 标题
     */
    @TableField(value = "title")
    @Schema(description = "标题")
    private String title;

    /**
     * 封面
     */
    @TableField(value = "coverImg")
    @Schema(description = "封面")
    private String coverImg;

    /**
     * 内容
     */
    @TableField(value = "content")
    @Schema(description = "内容")
    private String content;

    /**
     * 点赞数
     */
    @TableField(value = "thumbCount")
    @Schema(description = "点赞数")
    private Integer thumbCount;

    /**
     * 创建时间
     */
    @TableField(value = "createTime")
    @Schema(description = "创建时间")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "updateTime")
    @Schema(description = "更新时间")
    private Date updateTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}