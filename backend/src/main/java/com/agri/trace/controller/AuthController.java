package com.agri.trace.controller;

import com.agri.trace.common.R;
import com.agri.trace.dto.LoginDTO;
import com.agri.trace.dto.PasswordDTO;
import com.agri.trace.service.AuthService;
import com.agri.trace.vo.LoginVO;
import com.agri.trace.vo.UserVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.Valid;

@Tag(name = "认证登录")
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource
    private AuthService authService;

    @Operation(summary = "账号密码登录，返回 JWT")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok("登录成功", authService.login(dto));
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
}
