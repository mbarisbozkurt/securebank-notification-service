package com.securebank.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

@Configuration
public class RabbitMqConfig {

    @Bean
    public DirectExchange secureBankEventsExchange(
            @Value("${securebank.rabbitmq.exchange}") String exchangeName
    ) {
        return new DirectExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange secureBankEventsDeadLetterExchange(
            @Value("${securebank.rabbitmq.dead-letter-exchange}") String deadLetterExchangeName
    ) {
        return new DirectExchange(deadLetterExchangeName, true, false);
    }

    @Bean
    public Queue transferCompletedQueue(
            @Value("${securebank.rabbitmq.transfer-completed-queue}") String queueName,
            @Value("${securebank.rabbitmq.dead-letter-exchange}") String deadLetterExchangeName,
            @Value("${securebank.rabbitmq.transfer-completed-dead-letter-routing-key}") String deadLetterRoutingKey
    ) {
        return QueueBuilder
                .durable(queueName)
                .deadLetterExchange(deadLetterExchangeName)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }

    @Bean
    public Queue transferCompletedDeadLetterQueue(
            @Value("${securebank.rabbitmq.transfer-completed-dead-letter-queue}") String deadLetterQueueName
    ) {
        return QueueBuilder
                .durable(deadLetterQueueName)
                .build();
    }

    @Bean
    public Binding transferCompletedBinding(
            Queue transferCompletedQueue,
            DirectExchange secureBankEventsExchange,
            @Value("${securebank.rabbitmq.transfer-completed-routing-key}") String routingKey
    ) {
        return BindingBuilder
                .bind(transferCompletedQueue)
                .to(secureBankEventsExchange)
                .with(routingKey);
    }

    @Bean
    public Binding transferCompletedDeadLetterBinding(
            Queue transferCompletedDeadLetterQueue,
            DirectExchange secureBankEventsDeadLetterExchange,
            @Value("${securebank.rabbitmq.transfer-completed-dead-letter-routing-key}") String deadLetterRoutingKey
    ) {
        return BindingBuilder
                .bind(transferCompletedDeadLetterQueue)
                .to(secureBankEventsDeadLetterExchange)
                .with(deadLetterRoutingKey);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RetryOperationsInterceptor transferCompletedRetryInterceptor(
            @Value("${securebank.rabbitmq.retry.max-attempts}") int maxAttempts,
            @Value("${securebank.rabbitmq.retry.initial-interval-ms}") long initialIntervalMs,
            @Value("${securebank.rabbitmq.retry.multiplier}") double multiplier,
            @Value("${securebank.rabbitmq.retry.max-interval-ms}") long maxIntervalMs
    ) {
        return RetryInterceptorBuilder
                .stateless()
                .maxAttempts(maxAttempts)
                .backOffOptions(initialIntervalMs, multiplier, maxIntervalMs)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            RetryOperationsInterceptor transferCompletedRetryInterceptor
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(transferCompletedRetryInterceptor);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public ApplicationRunner rabbitMqDeclarableInitializer(
            RabbitAdmin rabbitAdmin,
            @Value("${securebank.rabbitmq.declare-on-startup}") boolean declareOnStartup
    ) {
        return args -> {
            if (declareOnStartup) {
                rabbitAdmin.initialize();
            }
        };
    }
}
