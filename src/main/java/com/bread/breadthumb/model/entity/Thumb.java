package com.bread.breadthumb.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 
 * @TableName thumb
 */
@TableName(value ="thumb")
@Data
@Schema(description = "Thumb")
public class Thumb implements Serializable {
    /**
     * 
     */
    @TableId(value = "id", type = IdType.AUTO)
    @Schema(description = "id")
    private Long id;

    /**
     * 点赞用户id
     */
    @TableField(value = "userId")
    @Schema(description = "点赞用户id")
    private Long userId;

    /**
     * 点赞blog id
     */
    @TableField(value = "blogId")
    @Schema(description = "点赞blog id")
    private Long blogId;

    /**
     * 创建时间
     */
    @TableField(value = "createTime")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}