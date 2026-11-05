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
        return issueToken(user);
    }

    /**
     * 为已通过身份校验的用户签发 JWT，并刷新最后登录时间。
     * 账号密码登录与微信登录共用同一套签发逻辑，保证三端 token 结构与权限完全一致。
     */
    @Transactional
    public LoginVO issueToken(SysUser user) {
        return issueToken(user, true);
    }

    /**
     * @param touchLoginTime 是否更新最后登录时间。刷新令牌换发时传 false，
     *                       否则每次静默续期都会把"最后登录"刷成当前时间，运营看板就失真了
     */
    @Transactional
    public LoginVO issueToken(SysUser user, boolean touchLoginTime) {
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
        String refreshToken = jwtUtil.generateRefresh(loginUser);

        if (touchLoginTime) {
            SysUser update = new SysUser();
            update.setId(user.getId());
            update.setLastLoginTime(LocalDateTime.now());
            userMapper.updateById(update);
        }

        return LoginVO.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpireSeconds())
                .refreshToken(refreshToken)
                .refreshExpiresIn(jwtUtil.getRefreshExpireSeconds())
                .user(userService.toVO(user))
                .permissions(new ArrayList<>(permissionCache.permissionsOf(user.getRoleCode())))
                .build();
    }

    /**
     * 用 refresh token 换发新的 token 对（rotation：旧 refresh token 随即作废）。
     *
     * <p>用户信息、角色、权限都在这里重新读库，因此管理员调整角色后，
     * 最长一个 access token 周期内所有端都会自动拿到新权限，无需用户重新登录。
     */
    public LoginVO refresh(String refreshToken) {
        LoginUser parsed;
        try {
            parsed = jwtUtil.parseRefresh(refreshToken);
        } catch (Exception e) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        SysUser user = userService.getById(parsed.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "账号不存在，请重新登录");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(ResultCode.FORBIDDEN, "账号已被禁用，请联系管理员");
        }
        return issueToken(user, false);
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
