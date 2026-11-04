package com.agri.trace.service;

import cn.hutool.crypto.digest.BCrypt;
import com.agri.trace.common.BizException;
import com.agri.trace.common.Constants;
import com.agri.trace.dto.LoginDTO;
import com.agri.trace.entity.SysRole;
import com.agri.trace.entity.SysUser;
import com.agri.trace.mapper.SysUserMapper;
import com.agri.trace.security.JwtUtil;
import com.agri.trace.security.PermissionCache;
import com.agri.trace.vo.LoginVO;
import com.agri.trace.vo.UserVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 登录认证测试：密码校验、状态校验、令牌签发
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private UserService userService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private PermissionCache permissionCache;

    @InjectMocks
    private AuthService authService;

    private SysUser user(String rawPassword) {
        SysUser user = new SysUser();
        user.setId(5L);
        user.setUsername("farmer01");
        user.setRealName("张大山");
        user.setPassword(BCrypt.hashpw(rawPassword, BCrypt.gensalt()));
        user.setRoleCode(Constants.ROLE_FARMER);
        user.setVillageId(1L);
        user.setStatus(1);
        return user;
    }

    private LoginDTO dto(String password) {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("farmer01");
        dto.setPassword(password);
        return dto;
    }

    @Test
    void shouldLoginWithCorrectPassword() {
        SysUser user = user("123456");
        when(userService.getByUsername("farmer01")).thenReturn(user);

        SysRole role = new SysRole();
        role.setRoleCode(Constants.ROLE_FARMER);
        role.setRoleName("农户");
        role.setDataScope("SELF");
        when(permissionCache.role(Constants.ROLE_FARMER)).thenReturn(role);
        when(permissionCache.permissionsOf(Constants.ROLE_FARMER))
                .thenReturn(Collections.singleton("planting:create"));
        when(jwtUtil.generate(any())).thenReturn("jwt-token");
        when(jwtUtil.getExpireSeconds()).thenReturn(3600L);
        when(userService.toVO(user)).thenReturn(new UserVO());

        LoginVO vo = authService.login(dto("123456"));

        assertEquals("jwt-token", vo.getToken());
        assertEquals("Bearer", vo.getTokenType());
        assertEquals(3600L, vo.getExpiresIn());
    }

    @Test
    void shouldRejectWrongPassword() {
        when(userService.getByUsername("farmer01")).thenReturn(user("123456"));

        BizException ex = assertThrows(BizException.class, () -> authService.login(dto("000000")));
        assertEquals(401, ex.getCode());
    }

    @Test
    void shouldRejectDisabledAccount() {
        SysUser user = user("123456");
        user.setStatus(0);
        when(userService.getByUsername("farmer01")).thenReturn(user);

        BizException ex = assertThrows(BizException.class, () -> authService.login(dto("123456")));
        assertEquals(403, ex.getCode());
    }
}
