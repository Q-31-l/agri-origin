package com.agri.trace.security;

import com.agri.trace.entity.SysRole;
import com.agri.trace.entity.SysRolePermission;
import com.agri.trace.mapper.SysRoleMapper;
import com.agri.trace.mapper.SysRolePermissionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色-权限内存缓存，避免每次请求查库；权限变更后调用 refresh()
 */
@Slf4j
@Component
public class PermissionCache {

    @Resource
    private SysRolePermissionMapper rolePermissionMapper;
    @Resource
    private SysRoleMapper roleMapper;

    private volatile Map<String, Set<String>> rolePermissions = Collections.emptyMap();
    private volatile Map<String, SysRole> roles = Collections.emptyMap();

    @PostConstruct
    public void init() {
        try {
            refresh();
        } catch (Exception e) {
            log.error("权限缓存初始化失败（请检查数据库是否已执行 sql/init.sql）：{}", e.getMessage());
        }
    }

    public void refresh() {
        List<SysRolePermission> list = rolePermissionMapper.selectList(Wrappers.emptyWrapper());
        Map<String, Set<String>> map = new HashMap<>();
        for (SysRolePermission rp : list) {
            map.computeIfAbsent(rp.getRoleCode(), k -> new HashSet<>()).add(rp.getPermCode());
        }
        this.rolePermissions = map;

        Map<String, SysRole> roleMap = new HashMap<>();
        for (SysRole role : roleMapper.selectList(Wrappers.emptyWrapper())) {
            roleMap.put(role.getRoleCode(), role);
        }
        this.roles = roleMap;
        log.info("权限缓存已加载：{} 个角色，{} 条授权", roleMap.size(), list.size());
    }

    public boolean has(String roleCode, String permCode) {
        return rolePermissions.getOrDefault(roleCode, Collections.emptySet()).contains(permCode);
    }

    public Set<String> permissionsOf(String roleCode) {
        return rolePermissions.getOrDefault(roleCode, Collections.emptySet());
    }

    public SysRole role(String roleCode) {
        return roles.get(roleCode);
    }
}
