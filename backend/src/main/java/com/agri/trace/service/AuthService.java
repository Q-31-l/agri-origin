package com.agri.trace.service;

import cn.hutool.crypto.digest.BCrypt;
import com.agri.trace.common.BizException;
import com.agri.trace.common.ResultCode;
import com.agri.trace.dto.LoginDTO;
import com.agri.trace.dto.PasswordDTO;
import com.agri.trace.entity.SysRole;
import com.agri.trace.entity.SysUser;
import com.agri.trace.mapper.SysUserMapper;
import com.agri.trace.security.JwtUtil;
import com.agri.trace.security.LoginUser;
import com.agri.trace.security.PermissionCache;
import com.agri.trace.security.UserContext;
import com.agri.trace.vo.LoginVO;
import com.agri.trace.vo.UserVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * 登录认证
 */
@Service
public class AuthService {

    @Resource
    private SysUserMapper userMapper;
    @Resource
    private UserService userService;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private PermissionCache permissionCache;

    @Transactional
    public LoginVO login(LoginDTO dto) {
        SysUser user = userService.getByUsername(dto.getUsername());
        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.UNAUTHORIZED, "账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }

        SysRole role = permissionCache.role(user.getRoleCode());
        LoginUser loginUser = LoginUser.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .realName(user.getRealName())
                .roleCode(user.getRoleCode())
                .roleName(role == null ? user.getRoleCode() : role.getRoleName())
                .dataScope(role == null ? "SELF" : role.getDataScope())
                .villageId(user.getVillageId())
                .farmId(user.getFarmId())
                .orgName(user.getOrgName())
                .build();

        String token = jwtUtil.generate(loginUser);

        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(update);

        return LoginVO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpireSeconds())
                .user(userService.toVO(user))
                .permissions(new ArrayList<>(permissionCache.permissionsOf(user.getRoleCode())))
                .build();
    }

    public UserVO profile() {
        return userService.toVO(userService.getById(UserContext.userId()));
    }

    @Transactional
    public void changePassword(PasswordDTO dto) {
        SysUser user = userService.getById(UserContext.userId());
        if (!BCrypt.checkpw(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.BAD_REQUEST, "原密码不正确");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(BCrypt.hashpw(dto.getNewPassword(), BCrypt.gensalt()));
        userMapper.updateById(update);
    }
}
