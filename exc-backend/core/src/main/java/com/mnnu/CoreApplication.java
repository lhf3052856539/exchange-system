package com.mnnu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @title 核心服务启动类
 * @notice 提供交易、用户、匹配等核心功能
 */
@SpringBootApplication
@MapperScan("com.mnnu.mapper")
@EnableScheduling
@EnableRabbit
public class CoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreApplication.class, args);
    }
}
