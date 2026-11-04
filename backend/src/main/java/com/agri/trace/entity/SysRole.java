package com.agri.trace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String roleCode;
    private String roleName;
    /** SELF / VILLAGE / ORG / ALL */
    private String dataScope;
    private Integer sortNo;
    private String remark;
    private LocalDateTime createTime;
}
