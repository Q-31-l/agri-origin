package com.agri.trace.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 工具：签发与解析（HS256）
 *
 * <p>双 token 设计：
 * <ul>
 *   <li><b>access token</b>（typ=access，默认 12 小时）：放进 Authorization 头，权限声明全在 payload 里，服务端无状态校验；</li>
 *   <li><b>refresh token</b>（typ=refresh，默认 7 天）：只在 {@code /auth/refresh} 使用，<b>不能</b>当访问凭证。</li>
 * </ul>
 * 两者用同一密钥但靠 {@code typ} 声明隔离：把 refresh token 塞进 Authorization 头会被
 * {@link #parse(String)} 直接拒绝，避免"长效凭证被当成短效凭证使用"。
 */
@Component
public class JwtUtil {

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-hours:12}")
    private long expireHours;

    @Value("${jwt.refresh-expire-days:7}")
    private long refreshExpireDays;

    /** 签发访问令牌 */
    public String generate(LoginUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .setSubject(String.valueOf(user.getUserId()))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim("username", user.getUsername())
                .claim("realName", user.getRealName())
                .claim("roleCode", user.getRoleCode())
                .claim("roleName", user.getRoleName())
                .claim("dataScope", user.getDataScope())
                .claim("villageId", user.getVillageId())
                .claim("farmId", user.getFarmId())
                .claim("orgName", user.getOrgName())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(SignatureAlgorithm.HS256, secret.getBytes(StandardCharsets.UTF_8))
                .compact();
    }

    /**
     * 签发刷新令牌。
     *
     * <p>只放 userId 和用户名，权限信息一律不带——换发时重新读库，
     * 保证角色或数据范围被管理员改过之后，下一次刷新即刻生效。
     */
    public String generateRefresh(LoginUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + refreshExpireDays * 86_400_000L);
        return Jwts.builder()
                .setSubject(String.valueOf(user.getUserId()))
                .setId(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim("username", user.getUsername())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(SignatureAlgorithm.HS256, secret.getBytes(StandardCharsets.UTF_8))
                .compact();
    }

    /**
     * 解析访问令牌。
     *
     * @throws JwtException token 非法、过期，或误把 refresh token 当成访问令牌
     */
    public LoginUser parse(String token) {
        Claims claims = parseClaims(token);
        if (TYPE_REFRESH.equals(claims.get(CLAIM_TYPE))) {
            throw new JwtException("refresh token 不能作为访问凭证");
        }
        return LoginUser.builder()
                .userId(Long.valueOf(claims.getSubject()))
                .username(str(claims, "username"))
                .realName(str(claims, "realName"))
                .roleCode(str(claims, "roleCode"))
                .roleName(str(claims, "roleName"))
                .dataScope(str(claims, "dataScope"))
                .villageId(number(claims, "villageId"))
                .farmId(number(claims, "farmId"))
                .orgName(str(claims, "orgName"))
                .build();
    }

    /**
     * 解析刷新令牌，只取回用户标识，权限由调用方重新查库。
     *
     * @throws JwtException token 非法、过期，或不是 refresh 类型
     */
    public LoginUser parseRefresh(String token) {
        Claims claims = parseClaims(token);
        if (!TYPE_REFRESH.equals(claims.get(CLAIM_TYPE))) {
            throw new JwtException("不是 refresh token");
        }
        return LoginUser.builder()
                .userId(Long.valueOf(claims.getSubject()))
                .username(str(claims, "username"))
                .build();
    }

    public long getExpireSeconds() {
        return expireHours * 3600L;
    }

    public long getRefreshExpireSeconds() {
        return refreshExpireDays * 86400L;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)
                .getBody();
    }

    private String str(Claims claims, String key) {
        Object v = claims.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private Long number(Claims claims, String key) {
        Object v = claims.get(key);
        if (v == null) {
            return null;
        }
        return ((Number) v).longValue();
    }
}
