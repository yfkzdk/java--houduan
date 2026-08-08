package com.yfkzdk.chatbot.modules.user.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体 — 对应 users 表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("users")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String email;

    private String nickname;

    private String avatarUrl;

    private Integer status;      // 1=启用, 0=禁用

    private Integer quotaTotal;  // 每月对话额度

    private Integer quotaUsed;   // 已用额度

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
