package com.mnnu.config;
/**
 * 当前用户参数解析器
 */

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 从请求中解析当前用户地址
 * 处理带有 @CurrentUser 注解的方法参数
 */
@Slf4j
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    //判断参数是否需要解析器处理
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        boolean supports = parameter.hasParameterAnnotation(CurrentUser.class) &&
                parameter.getParameterType().equals(String.class);
        if (supports) {
            log.info("Supports parameter: {}", parameter.getParameterName());
        }
        return supports;
    }

    //解析参数值
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        //从 NativeWebRequest 中提取原生的 HttpServletRequest 对象
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        //从请求属性中获取用户地址
        if (request != null) {
            String currentUser = (String) request.getAttribute("currentUser");
            log.info("Resolving currentUser: {}", currentUser);
            return currentUser;
        }
        log.warn("Request is null");
        return null;
    }
}
