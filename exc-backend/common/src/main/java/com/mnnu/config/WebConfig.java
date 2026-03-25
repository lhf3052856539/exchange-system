package com.mnnu.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 配置 - 注册自定义参数解析器
 */
@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    public WebConfig(CurrentUserArgumentResolver currentUserArgumentResolver) {
        log.info("=== Creating WebConfig with resolver: {}", currentUserArgumentResolver);
        this.currentUserArgumentResolver = currentUserArgumentResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        log.info("=== Adding CurrentUserArgumentResolver to resolvers");
        resolvers.add(currentUserArgumentResolver);
        log.info("=== Resolvers count after adding: {}", resolvers.size());
    }
}
