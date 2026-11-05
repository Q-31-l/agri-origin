package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 微信首次登录绑定已有平台账号
 */
@Data
public class WxBindDTO {

    @NotBlank(message = "绑定票据不能为空")
    private String bindTicket;

    @NotBlank(message = "请输入账号")
    private String username;

    @NotBlank(message = "请输入密码")
    private String password;
}
