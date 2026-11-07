package com.agri.trace.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 自助注册入参。
 *
 * <p>与 {@link UserDTO} 分开：注册只允许"农户 / 销售商"两种身份，
 * 字段范围更窄，避免把管理员创建用户的接口直接暴露给匿名请求。</p>
 */
public class RegisterDTO {

    @NotBlank(message = "请填写登录账号")
    @Pattern(regexp = "^[A-Za-z0-9_]{4,20}$", message = "账号需为 4–20 位字母、数字或下划线")
    private String username;

    @NotBlank(message = "请设置登录密码")
    @Size(min = 6, max = 32, message = "密码长度需在 6–32 位之间")
    private String password;

    @NotBlank(message = "请填写真实姓名")
    @Size(max = 20, message = "姓名最多 20 个字")
    private String realName;

    /** 手机号可选，填了就校验格式；不强制收集，避免不必要的个人信息 */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "请选择身份")
    private String roleCode;

    /** 农户必填：所属村 */
    private Long villageId;

    /** 销售商必填：机构名称 */
    private String orgName;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username == null ? null : username.trim();
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName == null ? null : realName.trim();
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone == null ? null : phone.trim();
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public Long getVillageId() {
        return villageId;
    }

    public void setVillageId(Long villageId) {
        this.villageId = villageId;
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName == null ? null : orgName.trim();
    }
}
