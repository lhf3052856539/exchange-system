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
        return new Queue(SystemConstants.MQQueue.TRADE_MATCH, true);
    }


    /**
     * 区块链事件队列
     */
    @Bean
    public Queue blockchainEventQueue() {
        return new Queue(SystemConstants.MQQueue.BLOCKCHAIN_EVENT, true);
    }



    /**
     * 直接交换机（默认）
     */
    @Bean
    public DirectExchange defaultExchange() {
        return new DirectExchange("exchange.default");
    }

    /**
     * 绑定队列到交换机
     */
    @Bean
    public Binding tradeMatchBinding(Queue tradeMatchQueue, DirectExchange defaultExchange) {
        return BindingBuilder.bind(tradeMatchQueue).to(defaultExchange).with(SystemConstants.MQQueue.TRADE_MATCH);
    }



    @Bean
    public Binding blockchainEventBinding(Queue blockchainEventQueue, DirectExchange defaultExchange) {
        return BindingBuilder.bind(blockchainEventQueue).to(defaultExchange).with(SystemConstants.MQQueue.BLOCKCHAIN_EVENT);
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
