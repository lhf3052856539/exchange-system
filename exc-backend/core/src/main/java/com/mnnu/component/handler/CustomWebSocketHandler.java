package com.mnnu.component.handler;
/**
 * WebSocket处理器
 */

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CustomWebSocketHandler extends TextWebSocketHandler {
    @Autowired
    private WebSocketService webSocketService;

    // 存储所有在线会话
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    /**
     * 连接建立处理
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String address = (String) session.getAttributes().get("address");
        if (address != null) {
            sessions.put(address, session);
            webSocketService.userOnline(address);
            log.info("WebSocket connected: {}", address);

            // 发送欢迎消息
            session.sendMessage(new TextMessage("{\"type\":\"connected\",\"message\":\"Connection established\"}"));
        } else {
            session.close(CloseStatus.BAD_DATA);
        }
    }

    /**
     * 接收消息处理
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String address = (String) session.getAttributes().get("address");
        String payload = message.getPayload();

        log.info("Received message from {}: {}", address, payload);

        // 处理消息
        webSocketService.handleMessage(address, payload);
    }

    /**
     * 连接关闭处理
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String address = (String) session.getAttributes().get("address");
        if (address != null) {
            sessions.remove(address);
            webSocketService.userOffline(address);
            log.info("WebSocket disconnected: {}", address);
        }
    }
    /**
     * 发送消息给指定用户
     */
    public static void sendToUser(String address, String message) {
        WebSocketSession session = sessions.get(address);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("Failed to send message to {}: {}", address, e.getMessage());
            }
        }
    }

    /**
     * 广播消息
     */
    public static void broadcast(String message) {
        sessions.values().forEach(session -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(message));
                } catch (IOException e) {
                    log.error("Failed to broadcast message: {}", e.getMessage());
                }
            }
        });
    }
}
