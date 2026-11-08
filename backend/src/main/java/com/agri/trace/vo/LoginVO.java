package com.agri.trace.vo;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
public class LoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String token;
    private String tokenType;
    /** 用包装类型：needBind=true 时整体为 null 会被 Jackson 直接省略，前端不会看到无意义的 0 */
    private Long expiresIn;

    /**
     * 刷新令牌：access token 过期后用它换新的一对 token。
     * 客户端需要持久化，但绝不能放进 Authorization 头（服务端会拒绝）。
     */
    private String refreshToken;
    private Long refreshExpiresIn;

    private UserVO user;
    private List<String> permissions;

    /**
     * 微信登录专用：true 表示该微信尚未绑定平台账号，
     * 此时 token/user/permissions 均为 null，前端应引导用户跳转绑定页。
     */
    private Boolean needBind;
    /** 绑定票据，仅 needBind=true 时返回，10 分钟内有效且一次性 */
    private String bindTicket;
    /**
     * 自助注册专用：true 表示"注册申请已提交、待系统管理员审核"，
     * 此时 token/user/permissions 均为 null，前端应提示用户等待审核而非直接登录。
     */
    private Boolean pendingAudit;
}
