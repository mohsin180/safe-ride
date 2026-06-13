package com.safe_ride.rides_service.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Publisher-side RabbitMQ wiring. Ride lifecycle events go out as JSON to a
 * durable topic exchange; notification-service consumes them. The exchange
 * name and JSON converter are kept in sync with the consumer side.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "saferide.notifications";

    @Bean
    public TopicExchange notificationsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        // Jackson 3 converter — Spring Boot 4 ships Jackson 3 (tools.jackson)
        // and no longer puts Jackson 2 on the classpath, so the older
        // Jackson2JsonMessageConverter fails to instantiate.
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
