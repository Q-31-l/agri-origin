package com.agri.trace.websocket;

import cn.hutool.json.JSONUtil;
import com.agri.trace.security.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时通知通道。
 *
 * <p>三端（PC-H5 / 小程序 / App）登录后共用同一条通道：
 * 任意端写入批次、异常处置、上链存证后，服务端广播事件，
 * 其他端的列表页收到后把本地缓存标记为脏，回到页面即自动刷新，
 * 从而实现"一端操作、三端同步"。
 *
 * <p>消息格式：{@code {"type":"batch|abnormal|chain","batchNo":"...","action":"...","message":"...","timestamp":1712...}}
 * 客户端心跳：发送文本 {@code ping}，服务端回 {@code pong}。
 */
@Slf4j
@Component
public class TraceWebSocketHandler extends TextWebSocketHandler {

    /** sessionId → 会话 */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    /** sessionId → 登录用户 */
    private final Map<String, LoginUser> users = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        Object attr = session.getAttributes().get(WsHandshakeInterceptor.ATTR_USER);
        if (attr instanceof LoginUser) {
            users.put(session.getId(), (LoginUser) attr);
        }
        log.info("WS 已连接：session={}, 当前在线 {} 个会话", session.getId(), sessions.size());
        send(session, JSONUtil.toJsonStr(message("open", null, "connected", "实时通知已连接")));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        if (payload == null || payload.isEmpty()) {
            return;
        }
        if ("ping".equalsIgnoreCase(payload.trim())) {
            send(session, "pong");
            return;
        }
        // 目前仅作为服务端推送通道使用，客户端上行消息只记录不处理
        log.debug("WS 收到上行消息：{}", payload);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.debug("WS 传输异常：session={}, {}", session.getId(), exception.getMessage());
        cleanup(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cleanup(session);
        log.info("WS 已断开：session={}, 剩余 {} 个会话", session.getId(), sessions.size());
    }

    /**
     * 广播业务事件，全部客户端（含发起方，发起方可直接忽略）。
     */
    public void broadcast(String type, String batchNo, String action, String message) {
        if (sessions.isEmpty()) {
            return;
        }
        String json = JSONUtil.toJsonStr(message(type, batchNo, action, message));
        sessions.values().forEach(s -> send(s, json));
        log.debug("WS 广播 {}：batchNo={}, action={}", type, batchNo, action);
    }

    public int onlineCount() {
        return sessions.size();
    }

    private Map<String, Object> message(String type, String batchNo, String action, String message) {
        Map<String, Object> map = new LinkedHashMap<>(8);
        map.put("type", type);
        map.put("batchNo", batchNo);
        map.put("action", action);
        map.put("message", message);
        map.put("timestamp", System.currentTimeMillis());
        return map;
    }

    private void send(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (Exception e) {
            log.debug("WS 发送失败：session={}, {}", session.getId(), e.getMessage());
        }
    }

    private void cleanup(WebSocketSession session) {
        sessions.remove(session.getId());
        users.remove(session.getId());
    }
}
