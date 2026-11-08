package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @JsonIgnore
    private String password;

    private String realName;
    private String phone;
    /** 身份证号（实名认证）：村级管理员/监管必填，其余身份使用功能前需补填 */
    private String idCard;
    private String avatar;
    /** 微信小程序 openid，null 表示未绑定 */
    private String wxOpenid;
    /** 微信 App（开放平台）openid —— 与小程序 openid 不是同一个值，必须分列存储 */
    private String wxAppOpenid;
    /** 微信开放平台 unionid（同一开放平台下多应用互通） */
    private String wxUnionid;
    private LocalDateTime wxBoundAt;
    private LocalDateTime wxAppBoundAt;
    private String roleCode;
    private String orgName;
    private Long villageId;
    private Long farmId;
    private Integer status;
    /** 注册审核状态：PENDING/APPROVED/REJECTED，null 表示无需审核（农户等直接注册） */
    private String auditStatus;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    private Integer deleted;
}
