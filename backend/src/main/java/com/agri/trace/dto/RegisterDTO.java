package com.agri.trace.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 自助注册入参。
 *
 * <p>与 {@link UserDTO} 分开：注册只允许溯源链上的五种身份（农户、生产主体、
 * 检测机构、物流企业、销售商），字段范围更窄，避免把管理员创建用户的接口
 * 直接暴露给匿名请求。</p>
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

    /** 身份证号：村级管理员/监管人员必填（实名认证），其余身份选填 */
    private String idCard;

    @NotBlank(message = "请选择身份")
    private String roleCode;

    /** 农户/生产主体必填：所属村（已存在村的 id）。定位/列表选不到时，可用 villageName 自填 */
    private Long villageId;

    /**
     * 自填村名（villageId 为空时使用）：注册页定位不到、列表里也没有的村，
     * 允许农户手动填写，服务端按"同名复用、无则新建"落到 base_village。
     */
    private String villageName;

    /** 自填村所属乡镇/县（选填，展示用） */
    private String regionName;

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

    public String getIdCard() {
        return idCard;
    }

    public void setIdCard(String idCard) {
        this.idCard = idCard == null ? null : idCard.trim();
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

    public String getVillageName() {
        return villageName;
    }

    public void setVillageName(String villageName) {
        this.villageName = villageName == null ? null : villageName.trim();
    }

    public String getRegionName() {
        return regionName;
    }

    public void setRegionName(String regionName) {
        this.regionName = regionName == null ? null : regionName.trim();
    }

    public String getOrgName() {
        return orgName;
    }

    public void setOrgName(String orgName) {
        this.orgName = orgName == null ? null : orgName.trim();
    }
}
