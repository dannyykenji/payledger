package com.payledger.notification;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EVENTS_EXCHANGE = "payledger.events.exchange";
    public static final String TRANSFER_NOTIFICATION_QUEUE = "payledger.transfer.notifications.queue";
    public static final String TRANSFER_COMPLETED_ROUTING_KEY = "transfer.completed";

    public static final String DLX_EXCHANGE = "payledger.dlx.exchange";
    public static final String TRANSFER_NOTIFICATION_DLQ = "payledger.transfer.notifications.dlq";
    public static final String TRANSFER_NOTIFICATION_DLQ_ROUTING_KEY = "transfer.notifications.dlq";

    @Bean
    public DirectExchange eventsExchange() {
        return new DirectExchange(EVENTS_EXCHANGE);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue transferNotificationQueue() {
        return QueueBuilder.durable(TRANSFER_NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", TRANSFER_NOTIFICATION_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue transferNotificationDlq() {
        return QueueBuilder.durable(TRANSFER_NOTIFICATION_DLQ).build();
    }

    @Bean
    public Binding transferNotificationBinding() {
        return BindingBuilder.bind(transferNotificationQueue())
                .to(eventsExchange())
                .with(TRANSFER_COMPLETED_ROUTING_KEY);
    }

    @Bean
    public Binding transferNotificationDlqBinding() {
        return BindingBuilder.bind(transferNotificationDlq())
                .to(deadLetterExchange())
                .with(TRANSFER_NOTIFICATION_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
