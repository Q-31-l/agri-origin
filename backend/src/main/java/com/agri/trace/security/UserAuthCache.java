package com.agri.trace.security;

import com.agri.trace.entity.SysRole;
import com.agri.trace.entity.SysUser;
import com.agri.trace.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账号状态与角色的短周期缓存。
 *
 * <p>背景：JWT 是无状态的，令牌里冻结了签发时的 {@code roleCode} 与 {@code villageId}。
 * 管理员"禁用账号"或"调整角色/所属村"后，旧令牌仍能在有效期内（默认 12 小时）
 * 按原角色通行——禁用形同虚设，降级也不生效。这里按 userId 做 60 秒缓存回查，
 * 在保持无状态的同时把风险窗口压到分钟级；账号变更时调用 {@link #evict(Long)} 立即失效。
 */
@Slf4j
@Component
public class UserAuthCache {

    /** 回查周期：越短越实时，越长越省库；60 秒对管理操作足够 */
    private static final long TTL_MS = 60_000L;

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private PermissionCache permissionCache;

    private final Map<Long, Entry> cache = new ConcurrentHashMap<>();

    /**
     * 用库中最新状态覆盖令牌里的身份信息。
     *
     * @return 最新登录用户；账号不存在或已被禁用时返回 {@code null}
     */
    public LoginUser refresh(LoginUser token) {
        if (token == null || token.getUserId() == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        Entry entry = cache.get(token.getUserId());
        if (entry == null || entry.expireAt <= now) {
            entry = load(token, now);
            cache.put(token.getUserId(), entry);
        }
        // 数据库瞬时不可用时沿用令牌身份，不把存储故障放大成全员 401
        return entry.tokenFallback ? token : entry.user;
    }

    /** 账号被禁用、改角色、改所属村、重置密码后调用，立即失效 */
    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    public void evictAll() {
        cache.clear();
    }

    private Entry load(LoginUser token, long now) {
        SysUser u;
        try {
            u = userMapper.selectById(token.getUserId());
        } catch (Exception e) {
            log.warn("账号状态回查失败，本次沿用令牌身份：userId={}, {}", token.getUserId(), e.getMessage());
            return new Entry(token, now + 5_000L, true);
        }
        if (u == null || u.getStatus() == null || u.getStatus() != 1) {
            log.info("令牌对应账号已禁用或不存在：userId={}, username={}", token.getUserId(), token.getUsername());
            return new Entry(null, now + TTL_MS, false);
        }
        SysRole role = permissionCache.role(u.getRoleCode());
        LoginUser fresh = LoginUser.builder()
                .userId(u.getId())
                .username(u.getUsername())
                .realName(u.getRealName())
                .roleCode(u.getRoleCode())
                .roleName(role == null ? u.getRoleCode() : role.getRoleName())
                .dataScope(role == null ? "SELF" : role.getDataScope())
                .villageId(u.getVillageId())
                .farmId(u.getFarmId())
                .orgName(u.getOrgName())
                .build();
        return new Entry(fresh, now + TTL_MS, false);
    }

    private static class Entry {
        private final LoginUser user;
        private final long expireAt;
        private final boolean tokenFallback;

        private Entry(LoginUser user, long expireAt, boolean tokenFallback) {
            this.user = user;
            this.expireAt = expireAt;
            this.tokenFallback = tokenFallback;
        }
    }
}
