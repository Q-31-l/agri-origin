package com.agri.trace.websocket;

import com.agri.trace.security.JwtUtil;
import com.agri.trace.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * WebSocket 握手鉴权：从 ?token= 取 JWT 并解析，失败直接拒绝握手。
 * 由于小程序 / H5 无法在 WebSocket 握手中自定义 Header，token 只能走 query 参数。
 */
@Slf4j
@Component
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER = "loginUser";

    @Resource
    private JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest)) {
            return false;
        }
        HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();
        String token = servletRequest.getParameter("token");
        if (token == null || token.trim().isEmpty()) {
            log.debug("WS 握手拒绝：缺少 token");
            return false;
        }
        try {
            LoginUser user = jwtUtil.parse(token);
            attributes.put(ATTR_USER, user);
            return true;
        } catch (Exception e) {
            log.debug("WS 握手拒绝：token 无效 - {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
