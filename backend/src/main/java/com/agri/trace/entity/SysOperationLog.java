package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_operation_log")
public class SysOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String username;
    private String roleCode;
    private String module;
    private String action;
    private String requestUri;
    private String method;
    private String params;
    private String ip;
    private Integer status;
    private String errorMsg;
    private Long costMs;
    private LocalDateTime createTime;
}
