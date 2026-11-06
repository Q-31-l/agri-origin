package com.agri.trace.security;

import com.agri.trace.common.Constants;
import com.agri.trace.common.R;
import com.agri.trace.common.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * 认证 + 授权拦截器
 * 1. 解析 JWT，写入 UserContext；
 * 2. 校验 @RequireRoles / @RequirePerm；
 * 3. 越权统一返回 403 / 未登录返回 401。
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private PermissionCache permissionCache;
    @Resource
    private UserAuthCache userAuthCache;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HEADER);
        if (header == null || header.trim().isEmpty()) {
            write(response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.UNAUTHORIZED, "未登录或登录状态已失效");
            return false;
        }
        String token = header.startsWith(PREFIX) ? header.substring(PREFIX.length()) : header;

        LoginUser user;
        try {
            user = jwtUtil.parse(token);
        } catch (Exception e) {
            log.debug("token 解析失败: {}", e.getMessage());
            write(response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
            return false;
        }
        // 令牌只证明"曾经是谁"。账号可能在签发后被禁用、改角色或换村，
        // 必须用库中最新状态覆盖，否则禁用与降级在令牌有效期内都不生效
        LoginUser effective = userAuthCache.refresh(user);
        if (effective == null) {
            log.warn("拒绝已失效账号的令牌：userId={}, username={}, uri={}",
                    user.getUserId(), user.getUsername(), request.getRequestURI());
            write(response, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.UNAUTHORIZED,
                    "账号已被禁用或不存在，请重新登录");
            return false;
        }
        UserContext.set(effective);

        HandlerMethod method = (HandlerMethod) handler;

        RequireRoles roles = method.getMethodAnnotation(RequireRoles.class);
        if (roles == null) {
            roles = method.getBeanType().getAnnotation(RequireRoles.class);
        }
        if (roles != null && !contains(roles.value(), effective.getRoleCode())) {
            log.warn("越权访问(角色)：user={}, role={}, uri={}",
                    effective.getUsername(), effective.getRoleCode(), request.getRequestURI());
            write(response, HttpServletResponse.SC_FORBIDDEN, ResultCode.FORBIDDEN, "无权访问：当前角色不具备该操作权限");
            return false;
        }

        RequirePerm perm = method.getMethodAnnotation(RequirePerm.class);
        if (perm == null) {
            perm = method.getBeanType().getAnnotation(RequirePerm.class);
        }
        if (perm != null && !Constants.ROLE_ADMIN.equals(effective.getRoleCode())
                && !permissionCache.has(effective.getRoleCode(), perm.value())) {
            log.warn("越权访问(权限)：user={}, role={}, perm={}, uri={}",
                    effective.getUsername(), effective.getRoleCode(), perm.value(), request.getRequestURI());
            write(response, HttpServletResponse.SC_FORBIDDEN, ResultCode.FORBIDDEN, "无权访问：缺少权限 " + perm.value());
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean contains(String[] arr, String value) {
        if (arr == null || value == null) {
            return false;
        }
        for (String s : arr) {
            if (s.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private void write(HttpServletResponse response, int status, int code, String message) throws Exception {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.write(objectMapper.writeValueAsString(R.fail(code, message)));
        }
    }
}
