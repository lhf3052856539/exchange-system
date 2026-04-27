package com.mnnu.config.rabbitmq;

import com.mnnu.constant.SystemConstants;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.mnnu.constant.SystemConstants.MQQueue.BLOCKCHAIN_EVENT;
import static com.mnnu.constant.SystemConstants.MQQueue.TRADE_MATCH;

/**
 * RabbitMQ 配置类
 * 负责声明队列、交换机和绑定关系
 */
@Configuration
public class RabbitConfig {

    /**
     * 交易匹配队列
     */
    @Bean
    public Queue tradeMatchQueue() {
        // durable=true 表示队列持久化
        return QueueBuilder.durable(TRADE_MATCH).build();
    }

    /**
     * 区块链事件队列
     */
    @Bean
    public Queue blockchainEventQueue() {
        // durable=true 表示队列持久化
        return QueueBuilder.durable(BLOCKCHAIN_EVENT).build();
    }


    /**
     * 直接交换机（默认）
     */
    @Bean
    public DirectExchange defaultExchange() {
        // durable=true 表示交换机持久化
        return ExchangeBuilder.directExchange("exchange.default").durable(true).build();
    }
    /**
     * 绑定队列到交换机
     */
    @Bean
    public Binding tradeMatchBinding(Queue tradeMatchQueue, DirectExchange defaultExchange) {
        return BindingBuilder.bind(tradeMatchQueue).to(defaultExchange).with(TRADE_MATCH);
    }



    @Bean
    public Binding blockchainEventBinding(Queue blockchainEventQueue, DirectExchange defaultExchange) {
        return BindingBuilder.bind(blockchainEventQueue).to(defaultExchange).with(BLOCKCHAIN_EVENT);
    }

    /**
     * JSON 消息转换器
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate 配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        rabbitTemplate.setExchange("exchange.default");

        // 开启发布确认（Publisher Confirm）
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                // 消息发送失败，记录日志或重试
                System.err.println("Message send failed: " + cause);
            }
        });

        // 开启返回回调（处理路由失败的情况）
        rabbitTemplate.setReturnsCallback(returned -> {
            System.err.println("Message returned: " + returned.getMessage() +
                    ", replyCode: " + returned.getReplyCode() +
                    ", replyText: " + returned.getReplyText());
        });

        return rabbitTemplate;
    }

    /**
     * 监听器容器工厂配置
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(3);
        factory.setMaxConcurrentConsumers(10);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
