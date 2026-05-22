package com.securebank.notification.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RabbitMqConfigTests {

    private final RabbitMqConfig config = new RabbitMqConfig();

    @Test
    void declaresTransferCompletedQueueWithDeadLetterSettings() {
        Queue queue = config.transferCompletedQueue(
                "securebank.transfer.completed",
                "securebank.events.dlx",
                "transfer.completed.dlq"
        );

        assertThat(queue.getName()).isEqualTo("securebank.transfer.completed");
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", "securebank.events.dlx")
                .containsEntry("x-dead-letter-routing-key", "transfer.completed.dlq");
    }

    @Test
    void declaresTransferCompletedDeadLetterTopology() {
        DirectExchange deadLetterExchange = config.secureBankEventsDeadLetterExchange("securebank.events.dlx");
        Queue deadLetterQueue = config.transferCompletedDeadLetterQueue("securebank.transfer.completed.dlq");
        Binding binding = config.transferCompletedDeadLetterBinding(
                deadLetterQueue,
                deadLetterExchange,
                "transfer.completed.dlq"
        );

        assertThat(deadLetterExchange.getName()).isEqualTo("securebank.events.dlx");
        assertThat(deadLetterExchange.isDurable()).isTrue();
        assertThat(deadLetterQueue.getName()).isEqualTo("securebank.transfer.completed.dlq");
        assertThat(deadLetterQueue.isDurable()).isTrue();
        assertThat(binding.getExchange()).isEqualTo("securebank.events.dlx");
        assertThat(binding.getRoutingKey()).isEqualTo("transfer.completed.dlq");
        assertThat(binding.getDestination()).isEqualTo("securebank.transfer.completed.dlq");
    }

    @Test
    void declaresRetryInterceptorAndListenerContainerFactory() {
        RetryOperationsInterceptor retryInterceptor = config.transferCompletedRetryInterceptor(
                3,
                2000,
                2.0,
                10000
        );

        SimpleRabbitListenerContainerFactory listenerFactory = config.rabbitListenerContainerFactory(
                mock(ConnectionFactory.class),
                mock(MessageConverter.class),
                retryInterceptor
        );

        assertThat(retryInterceptor).isNotNull();
        assertThat(listenerFactory).isNotNull();
    }
}
