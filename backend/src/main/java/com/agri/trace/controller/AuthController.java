package com.agri.trace.controller;

import com.agri.trace.common.R;
import com.agri.trace.dto.LoginDTO;
import com.agri.trace.dto.PasswordDTO;
import com.agri.trace.dto.RefreshTokenDTO;
import com.agri.trace.dto.RegisterDTO;
import com.agri.trace.dto.WxBindDTO;
import com.agri.trace.dto.WxLoginDTO;
import com.agri.trace.entity.SysUser;
import com.agri.trace.security.RegisterGuard;
import com.agri.trace.security.UserContext;
import com.agri.trace.service.AuthService;
import com.agri.trace.service.UserService;
import com.agri.trace.service.WxAuthService;
import com.agri.trace.vo.LoginVO;
import com.agri.trace.vo.UserVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@Tag(name = "认证登录")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    @Resource
    private WxAuthService wxAuthService;

    @Resource
    private UserService userService;

    @Resource
    private RegisterGuard registerGuard;

    @Operation(summary = "账号密码登录，返回 JWT")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok("登录成功", authService.login(dto));
    }

    /**
     * 自助注册：匿名可调用，注册成功直接签发 JWT，三端共用。
     *
     * <p>注册只放行"农户 / 销售商"两种身份——检测、物流、监管、村管、管理员
     * 掌握的是他人数据的处置权，必须由管理员实名开通，不能自助注册。
     * 具体校验在 {@link UserService#register(RegisterDTO)}，这里只负责
     * 防刷 + 注册即登录，省掉用户"注册完还要再输一遍密码"的一步。</p>
     */
    @Operation(summary = "自助注册（农户 / 销售商），成功即登录")
    @PostMapping("/register")
    public R<LoginVO> register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request) {
        String ip = registerGuard.check(request);
        Long userId = userService.register(dto);
        registerGuard.markSuccess(ip);
        SysUser user = userService.getById(userId);
        return R.ok("注册成功", authService.issueToken(user));
    }

    @Operation(summary = "刷新令牌：access token 过期后换发新的 token 对")
    @PostMapping("/refresh")
    public R<LoginVO> refresh(@Valid @RequestBody RefreshTokenDTO dto) {
        return R.ok("续期成功", authService.refresh(dto.getRefreshToken()));
    }

    @Operation(summary = "获取当前登录用户信息与权限")
    @GetMapping("/profile")
    public R<UserVO> profile() {
        return R.ok(authService.profile());
    }

    @Operation(summary = "退出登录（无状态，前端清除 token 即可）")
    @PostMapping("/logout")
    public R<Void> logout() {
        return R.ok();
    }

    @Operation(summary = "修改密码")
    @PutMapping("/password")
    public R<Void> password(@Valid @RequestBody PasswordDTO dto) {
        authService.changePassword(dto);
        return R.ok();
    }

    /* ---------------- 微信登录（小程序 + App 双端） ---------------- */

    @Operation(summary = "微信小程序一键登录：已绑定返回 JWT，未绑定返回 needBind + bindTicket")
    @PostMapping("/wx-login")
    public R<LoginVO> wxLogin(@RequestBody WxLoginDTO dto) {
        return wxResult(wxAuthService.login(dto, WxAuthService.Platform.MP));
    }

    @Operation(summary = "微信 App 一键登录（开放平台）：传 code，或旧版 SDK 的 openid + accessToken")
    @PostMapping("/wx-app-login")
    public R<LoginVO> wxAppLogin(@RequestBody WxLoginDTO dto) {
        return wxResult(wxAuthService.login(dto, WxAuthService.Platform.APP));
    }

    @Operation(summary = "微信首次登录：绑定已有平台账号并返回 JWT（票据自带来源端）")
    @PostMapping("/wx-bind")
    public R<LoginVO> wxBind(@Valid @RequestBody WxBindDTO dto) {
        return R.ok("绑定成功", wxAuthService.bind(dto));
    }

    @Operation(summary = "解除当前账号的微信绑定，platform=mp/app，默认 mp")
    @PostMapping("/wx-unbind")
    public R<Void> wxUnbind(@RequestParam(value = "platform", required = false) String platform) {
        wxAuthService.unbind(WxAuthService.Platform.of(platform));
        return R.ok();
    }

    @Operation(summary = "查询当前账号在指定端是否已绑定微信，platform=mp/app，默认 mp")
    @GetMapping("/wx-status")
    public R<Boolean> wxStatus(@RequestParam(value = "platform", required = false) String platform) {
        return R.ok(wxAuthService.isBound(UserContext.userId(), WxAuthService.Platform.of(platform)));
    }

    private R<LoginVO> wxResult(LoginVO vo) {
        if (Boolean.TRUE.equals(vo.getNeedBind())) {
            return R.ok("该微信尚未绑定平台账号", vo);
        }
        return R.ok("登录成功", vo);
    }
}
