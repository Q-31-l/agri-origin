package com.agri.trace.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 刷新令牌请求：access token 过期后，用它换发新的一对 token
 */
@Data
public class RefreshTokenDTO {

    @NotBlank(message = "缺少刷新令牌")
    private String refreshToken;
}
