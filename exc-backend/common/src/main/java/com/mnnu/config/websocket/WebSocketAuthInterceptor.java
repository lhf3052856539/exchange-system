package com.mnnu.config.websocket;
/**
 * WebSocket 认证拦截器
 * 专门拦截 WebSocket 的握手请求（建立连接前的认证）
 */

import com.mnnu.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

@Slf4j
@Component
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest servletRequest = ((ServletServerHttpRequest) request).getServletRequest();

            String token = servletRequest.getParameter("token");
            if (token == null || token.isEmpty()) {
                log.warn("WebSocket handshake without token");
                return false;
            }

            if (!jwtUtil.validateToken(token)) {
                log.warn("Invalid JWT token");
                return false;
            }

            String address = jwtUtil.getAddressFromToken(token);
            if (address == null || address.isEmpty()) {
                log.warn("Failed to get address from token");
                return false;
            }

            attributes.put("address", address);
            attributes.put("userId", address);

            // 关键：设置 Spring Security 用户名，用于 STOMP 消息路由
            attributes.put("user", address);

            log.info("WebSocket handshake success: {}", address);
        }
        return true;
    }
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }
}
