package com.mnnu.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.Key;
import java.util.Date;

/**
 * JWT 工具类
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret:mySecretKeyForJwtTokenGenerationAndValidation123456}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private Long jwtExpiration;

    private Key key;

    //密钥初始化,在 Bean 创建后自动执行
    @PostConstruct
    //将字符串密钥转换为安全的 Key 对象
    public void init() {
        this.key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
    }

    /**
     * 从 Token 中获取用户地址
     */
    public String getAddressFromToken(String token) {
        try {
            //构建 JWT 解析器
            Claims claims = Jwts.parserBuilder()
                    //设置签名密钥进行验证
                    .setSigningKey(key)
                    .build()
                    //解析 Token 并获取 Claims（负载部分）
                    .parseClaimsJws(token)
                    .getBody();

            //从 Claims 中提取自定义字段 "address"
            return claims.get("address", String.class);
        } catch (Exception e) {
            log.error("Failed to parse JWT token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            //验证Token 格式是否正确，签名是否匹配，Token 是否过期
            Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 生成 Token（用于测试）
     */
    public String generateToken(String address) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                //设置主题为用户地址
                .setSubject(address)
                .claim("address", address)
                //设置签发时间为当前时间
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}

