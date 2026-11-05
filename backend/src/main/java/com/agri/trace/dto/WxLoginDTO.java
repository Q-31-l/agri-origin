package com.agri.trace.dto;

import lombok.Data;

/**
 * 微信登录凭证。
 *
 * <p>小程序端：{@code uni.login} 拿到一次性 {@code code}，后端调 jscode2session 换 openid。
 *
 * <p>App 端两种形态都支持：
 * <ul>
 *   <li>新版 uni-app / 微信 OpenSDK：返回 {@code code}，与小程序路径一致；</li>
 *   <li>部分老版本：只返回 {@code openid + access_token}，此时后端调 {@code sns/auth} 校验令牌有效性。</li>
 * </ul>
 * 因此三个字段都允许为空，具体必填项由 WxAuthService 按平台校验。
 */
@Data
public class WxLoginDTO {

    /** 微信授权临时凭证 */
    private String code;

    /** App 端旧版 SDK 直接返回的 openid（与 accessToken 配对使用） */
    private String openid;

    /** App 端旧版 SDK 返回的 access_token，用于向微信校验 openid 的真实性 */
    private String accessToken;

    /** 可从 uni.getUserProfile 带入，仅作展示，不参与鉴权 */
    private String nickName;
    private String avatarUrl;
}
