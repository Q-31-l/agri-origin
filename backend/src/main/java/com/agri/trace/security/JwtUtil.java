package com.agri.trace.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：签发与解析（HS256）
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-hours:12}")
    private long expireHours;

    public String generate(LoginUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireHours * 3600_000L);
        return Jwts.builder()
                .setSubject(String.valueOf(user.getUserId()))
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
     * @throws JwtException token 非法或过期
     */
    public LoginUser parse(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(secret.getBytes(StandardCharsets.UTF_8))
                .parseClaimsJws(token)
                .getBody();
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

    public long getExpireSeconds() {
        return expireHours * 3600L;
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
