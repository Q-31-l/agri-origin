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
        UserContext.set(user);

        HandlerMethod method = (HandlerMethod) handler;

        RequireRoles roles = method.getMethodAnnotation(RequireRoles.class);
        if (roles == null) {
            roles = method.getBeanType().getAnnotation(RequireRoles.class);
        }
        if (roles != null && !contains(roles.value(), user.getRoleCode())) {
            log.warn("越权访问(角色)：user={}, role={}, uri={}", user.getUsername(), user.getRoleCode(), request.getRequestURI());
            write(response, HttpServletResponse.SC_FORBIDDEN, ResultCode.FORBIDDEN, "无权访问：当前角色不具备该操作权限");
            return false;
        }

        RequirePerm perm = method.getMethodAnnotation(RequirePerm.class);
        if (perm == null) {
            perm = method.getBeanType().getAnnotation(RequirePerm.class);
        }
        if (perm != null && !Constants.ROLE_ADMIN.equals(user.getRoleCode())
                && !permissionCache.has(user.getRoleCode(), perm.value())) {
            log.warn("越权访问(权限)：user={}, role={}, perm={}, uri={}",
                    user.getUsername(), user.getRoleCode(), perm.value(), request.getRequestURI());
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
