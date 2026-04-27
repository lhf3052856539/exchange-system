package com.mnnu.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        // 使用 Apache HttpClient 作为 RestTemplate 的底层请求工厂
        // 它提供了连接池和更精细的控制
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();

        // 设置连接超时时间，单位为毫秒
        // 10秒内必须与目标服务器建立连接，否则报错
        factory.setConnectTimeout(10000); // 10 seconds

        // 设置读取超时时间，单位为毫秒
        // 连接建立后，等待服务器返回数据的最长时间
        // 对于重型查询，这个值必须设置得长一些
        factory.setReadTimeout(60000); // 60 seconds

        return new RestTemplate(factory);
    }
}
