package com.mnnu.config;

/**
 * JWT 认证拦截器
 */

import com.mnnu.annotation.Anonymous;
import com.mnnu.exception.BusinessException;
import com.mnnu.utils.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import org.springframework.web.method.HandlerMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        log.info("=== Intercepting: {} {}", method, uri);

        // 跳过预检请求（CORS）
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // 如果处理器不是 HandlerMethod (例如静态资源), 放行
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // 关键修复：检查方法上是否有 @Anonymous 注解
        if (handlerMethod.hasMethodAnnotation(Anonymous.class)) {
            log.info("Skipping auth for @Anonymous annotated method: {}",
                    handlerMethod.getMethod().getName());
            return true;
        }

        // 只跳过注册和登录接口
        if (uri.startsWith("/apis/user/register") ||
                uri.startsWith("/apis/user/login")) {
            log.info("Skipping auth for register/login");
            return true;
        }

        //从请求头获取 token
        String token = getToken(request);
        log.info("Token present: {}", token != null);

        if (token == null || token.isEmpty()) {
            log.error("Missing token for URI: {}", uri);
            throw new BusinessException(401, "未登录");
        }

        if (!jwtUtil.validateToken(token)) {
            log.error("Invalid token for URI: {}", uri);
            throw new BusinessException(401, "无效的 token");
        }

        //从 Token 中解析出用户的钱包地址
        String address = jwtUtil.getAddressFromToken(token);
        log.info("Address from token: {}", address);

        if (address == null || address.isEmpty()) {
            log.error("Failed to get address from token");
            throw new BusinessException(401, "无效的 token");
        }

        //将解析的地址存储到请求属性中，键名为 "currentUser"
        request.setAttribute("currentUser", address);
        log.info("Set currentUser attribute: {}", address);
        return true;
    }

    private String getToken(HttpServletRequest request) {
        //从 HTTP 请求头中获取 Authorization 字段
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            //去掉 "Bearer" 前缀（7 个字符），返回纯 Token
            return authHeader.substring(7);
        }
        return null;
    }
}

