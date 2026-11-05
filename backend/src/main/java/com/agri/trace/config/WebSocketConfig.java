package com.agri.trace.config;

import com.agri.trace.websocket.TraceWebSocketHandler;
import com.agri.trace.websocket.WsHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import javax.annotation.Resource;

/**
 * WebSocket 注册：对外地址 /api/ws?token=xxx
 * （context-path 为 /api，因此这里注册的相对路径是 /ws）
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Resource
    private TraceWebSocketHandler traceWebSocketHandler;

    @Resource
    private WsHandshakeInterceptor wsHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(traceWebSocketHandler, "/ws")
                .addInterceptors(wsHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
