package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class UserDTO {

    private Long id;

    @NotBlank(message = "请填写登录账号")
    private String username;

    /** 新增必填，编辑时留空表示不修改 */
    private String password;

    @NotBlank(message = "请填写真实姓名")
    private String realName;

    private String phone;

    @NotBlank(message = "请选择角色")
    private String roleCode;

    private String orgName;
    private Long villageId;
    private Long farmId;
    private Integer status = 1;
}
