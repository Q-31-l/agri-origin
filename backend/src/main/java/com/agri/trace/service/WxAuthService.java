package com.agri.trace.service;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.agri.trace.common.BizException;
import com.agri.trace.common.ResultCode;
import com.agri.trace.dto.WxBindDTO;
import com.agri.trace.dto.WxLoginDTO;
import com.agri.trace.entity.SysUser;
import com.agri.trace.mapper.SysUserMapper;
import com.agri.trace.security.UserContext;
import com.agri.trace.vo.LoginVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信登录（小程序 + App 双端）。
 *
 * <p>流程：客户端唤起微信授权拿 code → 后端换取 openid →
 * <ol>
 *   <li>openid 已绑定平台账号：直接签发 JWT（含 refresh token），与账号密码登录完全等价；</li>
 *   <li>openid 未绑定：返回 needBind + 一次性 bindTicket，前端跳绑定页；</li>
 *   <li>用户提交账号密码 + bindTicket：校验通过后把 openid 落到该账号，再签发 JWT。</li>
 * </ol>
 *
 * <p><b>为什么小程序和 App 要两套配置、两个字段：</b>
 * 小程序走 {@code sns/jscode2session}，App 走开放平台 {@code sns/oauth2/access_token}，
 * appid 不是同一个，换回来的 openid 也不同（openid 是「应用 + 用户」维度的）。
 * 只有 unionid 在同一个开放平台账号下才一致，所以必须分列存储，
 * 否则会出现「App 登录命中了小程序的绑定关系」这种串号问题。
 *
 * <p>某一端未配置 appid/secret 时，该端自动进入 mock 模式：不请求微信服务器，
 * 使用该端固定的模拟 openid，便于本地无资质时完整走通「首次绑定 → 直接登录」链路。
 * mock 模式在启动日志与每次登录日志中都会显式提示，不会伪装成真实微信登录。
 */
@Slf4j
@Service
public class WxAuthService {

    /** 小程序：code 换 openid */
    private static final String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    /** App（开放平台）：code 换 access_token + openid */
    private static final String OAUTH2_ACCESS_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";
    /** App（开放平台）：校验 access_token 是否仍然有效 */
    private static final String SNS_AUTH_URL = "https://api.weixin.qq.com/sns/auth";

    /** 绑定票据有效期：10 分钟 */
    private static final long TICKET_TTL_MILLIS = 10 * 60 * 1000L;

    /** mock 模式下的固定 openid，保证多次登录命中同一条绑定关系 */
    private static final String MOCK_MP_OPENID = "mock-openid-local-demo";
    private static final String MOCK_APP_OPENID = "mock-app-openid-local-demo";

    /** 微信登录来源端 */
    public enum Platform {
        /** 微信小程序 */
        MP("小程序"),
        /** App（微信开放平台） */
        APP("App");

        private final String label;

        Platform(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** 前端传 "app" 时按 App 处理，其余一律按小程序处理 */
        public static Platform of(String value) {
            return value != null && "app".equalsIgnoreCase(value.trim()) ? APP : MP;
        }
    }

    /** ---------------- 小程序配置 ---------------- */
    @Value("${app.wx.appid:}")
    private String appid;

    @Value("${app.wx.secret:}")
    private String secret;

    /** ---------------- App（开放平台）配置 ---------------- */
    @Value("${app.wx.app-appid:}")
    private String appAppid;

    @Value("${app.wx.app-secret:}")
    private String appSecret;

    @Value("${app.wx.mock-enabled:false}")
    private boolean mockEnabled;

    @Value("${app.wx.timeout-ms:5000}")
    private int timeoutMs;

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private UserService userService;
    @Resource
    private AuthService authService;

    /** bindTicket → 待绑定微信身份 */
    private final Map<String, PendingBind> tickets = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ 登录

    /**
     * 微信登录：已绑定直接返回 token，未绑定返回 needBind + bindTicket。
     */
    @Transactional
    public LoginVO login(WxLoginDTO dto, Platform platform) {
        WxIdentity identity = resolveIdentity(dto, platform);
        SysUser bound = findByOpenid(platform, identity.getOpenid());
        if (bound == null) {
            String ticket = issueTicket(identity, platform);
            log.info("微信{}登录：openid={} 尚未绑定平台账号，已下发绑定票据", platform.label(), mask(identity.getOpenid()));
            return LoginVO.builder().needBind(true).bindTicket(ticket).build();
        }
        if (bound.getStatus() == null || bound.getStatus() != 1) {
            throw new BizException(ResultCode.FORBIDDEN, "该微信绑定的账号已被禁用，请联系管理员");
        }
        log.info("微信{}登录：openid={} 命中账号 {}", platform.label(), mask(identity.getOpenid()), bound.getUsername());
        return authService.issueToken(bound);
    }

    /**
     * 微信首次登录：用平台账号密码认领当前微信。
     */
    @Transactional
    public LoginVO bind(WxBindDTO dto) {
        PendingBind pending = tickets.remove(dto.getBindTicket());
        if (pending == null || pending.getExpireAt() < System.currentTimeMillis()) {
            throw new BizException(ResultCode.BAD_REQUEST, "绑定票据已失效，请重新微信登录");
        }
        Platform platform = Platform.of(pending.getPlatform());

        SysUser user = userService.getByUsername(dto.getUsername());
        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }
        String current = openidOf(user, platform);
        if (current != null && !current.isEmpty() && !current.equals(pending.getOpenid())) {
            throw new BizException(ResultCode.CONFLICT,
                    "该账号已绑定其他微信" + platform.label() + "，请先在账号安全中解绑");
        }
        SysUser occupant = findByOpenid(platform, pending.getOpenid());
        if (occupant != null && !occupant.getId().equals(user.getId())) {
            throw new BizException(ResultCode.CONFLICT,
                    "该微信已绑定账号 " + occupant.getUsername() + "，请直接微信登录");
        }

        SysUser update = new SysUser();
        update.setId(user.getId());
        if (platform == Platform.APP) {
            update.setWxAppOpenid(pending.getOpenid());
            update.setWxAppBoundAt(LocalDateTime.now());
        } else {
            update.setWxOpenid(pending.getOpenid());
            update.setWxBoundAt(LocalDateTime.now());
        }
        if (pending.getUnionid() != null && !pending.getUnionid().isEmpty()) {
            update.setWxUnionid(pending.getUnionid());
        }
        userMapper.updateById(update);

        // 让后续签发的 LoginVO 带上最新绑定状态
        applyOpenid(user, platform, pending.getOpenid());
        log.info("微信{}绑定成功：openid={} → 账号 {}", platform.label(), mask(pending.getOpenid()), user.getUsername());
        return authService.issueToken(user);
    }

    /**
     * 解除当前登录账号在指定端的微信绑定。
     */
    @Transactional
    public void unbind(Platform platform) {
        Long userId = UserContext.userId();
        SysUser user = userService.getById(userId);
        if (openidOf(user, platform) == null || openidOf(user, platform).isEmpty()) {
            return;
        }
        // @TableField 未设 update-strategy，MyBatis-Plus 默认忽略 null 字段，
        // 需要用 UpdateWrapper 显式置空
        if (platform == Platform.APP) {
            userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                    .eq(SysUser::getId, userId)
                    .set(SysUser::getWxAppOpenid, null)
                    .set(SysUser::getWxAppBoundAt, null));
        } else {
            userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                    .eq(SysUser::getId, userId)
                    .set(SysUser::getWxOpenid, null)
                    .set(SysUser::getWxBoundAt, null));
        }
        log.info("微信{}解绑成功：账号 {}", platform.label(), user.getUsername());
    }

    /** 当前账号在指定端是否已绑定微信 */
    public boolean isBound(Long userId, Platform platform) {
        SysUser user = userService.getById(userId);
        String openid = openidOf(user, platform);
        return openid != null && !openid.isEmpty();
    }

    // ------------------------------------------------------------- 内部实现

    private String openidOf(SysUser user, Platform platform) {
        if (user == null) {
            return null;
        }
        return platform == Platform.APP ? user.getWxAppOpenid() : user.getWxOpenid();
    }

    private void applyOpenid(SysUser user, Platform platform, String openid) {
        if (platform == Platform.APP) {
            user.setWxAppOpenid(openid);
            user.setWxAppBoundAt(LocalDateTime.now());
        } else {
            user.setWxOpenid(openid);
            user.setWxBoundAt(LocalDateTime.now());
        }
    }

    private SysUser findByOpenid(Platform platform, String openid) {
        if (openid == null || openid.isEmpty()) {
            return null;
        }
        if (platform == Platform.APP) {
            return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getWxAppOpenid, openid).last("limit 1"));
        }
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getWxOpenid, openid).last("limit 1"));
    }

    /**
     * 按平台解析微信身份：优先用 code 换，App 端兼容旧版 SDK 直接给的 openid + access_token。
     */
    private WxIdentity resolveIdentity(WxLoginDTO dto, Platform platform) {
        String code = dto.getCode() == null ? null : dto.getCode().trim();
        if (code != null && !code.isEmpty()) {
            return code2Session(code, platform);
        }
        if (platform == Platform.APP) {
            return verifyAppOpenid(dto.getOpenid(), dto.getAccessToken());
        }
        throw new BizException(ResultCode.BAD_REQUEST, "缺少微信登录凭证 code");
    }

    /**
     * App 端旧版 SDK 只返回 openid + access_token，这里必须回头找微信校验一次，
     * 否则客户端可以随意伪造 openid 直接登录任意账号。
     */
    private WxIdentity verifyAppOpenid(String openid, String accessToken) {
        String oid = openid == null ? null : openid.trim();
        String token = accessToken == null ? null : accessToken.trim();
        if (oid == null || oid.isEmpty() || token == null || token.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "缺少微信登录凭证 code");
        }
        if (isMock(Platform.APP)) {
            log.warn("微信App 处于 mock 模式（未配置 app.wx.app-appid/app-secret），跳过 access_token 校验");
        } else {
            Map<String, Object> params = new HashMap<>(4);
            params.put("access_token", token);
            params.put("openid", oid);
            String body;
            try {
                body = HttpUtil.get(SNS_AUTH_URL, params, timeoutMs);
            } catch (Exception e) {
                log.error("微信 sns/auth 请求失败: {}", e.getMessage());
                throw new BizException(ResultCode.SERVER_ERROR, "微信服务暂时不可用，请稍后重试");
            }
            JSONObject json = JSONUtil.parseObj(body);
            Integer errcode = json.getInt("errcode");
            if (errcode == null || errcode != 0) {
                throw new BizException(ResultCode.UNAUTHORIZED, "微信登录凭证无效，请重新授权");
            }
        }
        WxIdentity identity = new WxIdentity();
        identity.setOpenid(oid);
        return identity;
    }

    /**
     * 用 code 换取微信身份。小程序走 jscode2session，App 走开放平台 oauth2/access_token。
     */
    private WxIdentity code2Session(String code, Platform platform) {
        if (isMock(platform)) {
            log.warn("微信{}登录处于 mock 模式（未配置 appid/secret），openid 为固定模拟值，仅用于本地联调", platform.label());
            WxIdentity identity = new WxIdentity();
            identity.setOpenid(platform == Platform.APP ? MOCK_APP_OPENID : MOCK_MP_OPENID);
            return identity;
        }

        boolean app = platform == Platform.APP;
        Map<String, Object> params = new HashMap<>(8);
        params.put("appid", (app ? appAppid : appid).trim());
        params.put("secret", (app ? appSecret : secret).trim());
        params.put("grant_type", "authorization_code");
        if (app) {
            params.put("code", code);
        } else {
            params.put("js_code", code);
        }

        String body;
        try {
            body = HttpUtil.get(app ? OAUTH2_ACCESS_TOKEN_URL : CODE2SESSION_URL, params, timeoutMs);
        } catch (Exception e) {
            log.error("微信{}换取 openid 请求失败: {}", platform.label(), e.getMessage());
            throw new BizException(ResultCode.SERVER_ERROR, "微信服务暂时不可用，请稍后重试");
        }
        log.debug("微信{}换取 openid 响应: {}", platform.label(), body);
        JSONObject json = JSONUtil.parseObj(body);
        Integer errcode = json.getInt("errcode");
        if (errcode != null && errcode != 0) {
            throw new BizException(ResultCode.UNAUTHORIZED,
                    "微信登录失败：" + json.getStr("errmsg", "errcode=" + errcode));
        }
        String openid = json.getStr("openid");
        if (openid == null || openid.isEmpty()) {
            throw new BizException(ResultCode.UNAUTHORIZED, "微信登录失败：未获取到 openid");
        }
        WxIdentity identity = new WxIdentity();
        identity.setOpenid(openid);
        identity.setUnionid(json.getStr("unionid"));
        return identity;
    }

    private boolean isMock(Platform platform) {
        if (mockEnabled) {
            return true;
        }
        String id = platform == Platform.APP ? appAppid : appid;
        String key = platform == Platform.APP ? appSecret : secret;
        return id == null || id.trim().isEmpty() || key == null || key.trim().isEmpty();
    }

    private String issueTicket(WxIdentity identity, Platform platform) {
        evictExpiredTickets();
        String ticket = UUID.randomUUID().toString().replace("-", "");
        PendingBind pending = new PendingBind();
        pending.setOpenid(identity.getOpenid());
        pending.setUnionid(identity.getUnionid());
        pending.setPlatform(platform.name());
        pending.setExpireAt(System.currentTimeMillis() + TICKET_TTL_MILLIS);
        tickets.put(ticket, pending);
        return ticket;
    }

    private void evictExpiredTickets() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(e -> e.getValue().getExpireAt() < now);
    }

    private String mask(String openid) {
        if (openid == null || openid.length() <= 8) {
            return openid;
        }
        return openid.substring(0, 4) + "****" + openid.substring(openid.length() - 4);
    }

    @Data
    private static class WxIdentity {
        private String openid;
        private String unionid;
    }

    @Data
    private static class PendingBind {
        private String openid;
        private String unionid;
        /** MP / APP，绑定成功后按此决定写入哪一列 */
        private String platform;
        private long expireAt;
    }
}
