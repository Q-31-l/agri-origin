package com.agri.trace.security;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 当前登录用户上下文对象（由 JWT 解析得到，无状态）
 */
@Data
@Builder
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String realName;
    private String roleCode;
    private String roleName;
    /** SELF / VILLAGE / ORG / ALL */
    private String dataScope;
    private Long villageId;
    private Long farmId;
    private String orgName;
    private List<String> permissions;
}
