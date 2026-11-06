package com.agri.trace.security;

import com.agri.trace.entity.SysRole;
import com.agri.trace.entity.SysUser;
import com.agri.trace.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * 令牌身份回查：禁用账号不得继续通行，改角色后必须取到新角色
 */
@ExtendWith(MockitoExtension.class)
class UserAuthCacheTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private PermissionCache permissionCache;

    @InjectMocks
    private UserAuthCache userAuthCache;

    private LoginUser tokenOf(String roleCode) {
        return LoginUser.builder().userId(5L).username("farmer02").roleCode(roleCode).villageId(1L).build();
    }

    private SysUser dbUser(int status, String roleCode) {
        SysUser u = new SysUser();
        u.setId(5L);
        u.setUsername("farmer02");
        u.setRealName("王二");
        u.setRoleCode(roleCode);
        u.setStatus(status);
        u.setVillageId(1L);
        return u;
    }

    @Test
    void shouldRejectDisabledAccountEvenWithValidToken() {
        when(userMapper.selectById(5L)).thenReturn(dbUser(0, "FARMER"));
        assertNull(userAuthCache.refresh(tokenOf("FARMER")));
    }

    @Test
    void shouldRejectMissingAccount() {
        when(userMapper.selectById(5L)).thenReturn(null);
        assertNull(userAuthCache.refresh(tokenOf("FARMER")));
    }

    @Test
    void shouldUseLatestRoleInsteadOfTokenRole() {
        SysRole role = new SysRole();
        role.setRoleCode("VILLAGE_ADMIN");
        role.setRoleName("村级管理员");
        role.setDataScope("VILLAGE");
        when(userMapper.selectById(5L)).thenReturn(dbUser(1, "VILLAGE_ADMIN"));
        when(permissionCache.role("VILLAGE_ADMIN")).thenReturn(role);

        LoginUser fresh = userAuthCache.refresh(tokenOf("REGULATOR"));
        assertEquals("VILLAGE_ADMIN", fresh.getRoleCode());
        assertEquals("VILLAGE", fresh.getDataScope());
    }

    @Test
    void shouldPickUpChangeAfterEvict() {
        when(userMapper.selectById(5L)).thenReturn(dbUser(1, "FARMER"));
        assertEquals("FARMER", userAuthCache.refresh(tokenOf("FARMER")).getRoleCode());

        // 管理员把账号禁用后立即 evict，下一次请求必须被拒
        when(userMapper.selectById(5L)).thenReturn(dbUser(0, "FARMER"));
        userAuthCache.evict(5L);
        assertNull(userAuthCache.refresh(tokenOf("FARMER")));
    }
}
