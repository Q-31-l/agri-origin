package com.agri.trace.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String realName;
    private String phone;
    private String idCard;
    private String avatar;
    private String roleCode;
    private String roleName;
    private String dataScope;
    private String orgName;
    private Long villageId;
    private String villageName;
    private Long farmId;
    private String farmName;
    private Integer status;
    /** 注册审核状态：PENDING/APPROVED/REJECTED，null=无需审核 */
    private String auditStatus;
}
