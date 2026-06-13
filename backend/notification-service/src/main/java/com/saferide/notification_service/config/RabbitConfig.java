package com.saferide.notification_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Consumer-side RabbitMQ wiring. Declares the same topic exchange as the
 * producer plus a durable queue bound to {@code ride.#}, and a JSON
 * converter set to INFERRED type precedence.
 *
 * <p>The producer (rides-service) stamps its own fully-qualified class name
 * in the {@code __TypeId__} header. INFERRED makes this converter ignore
 * that header and deserialize into the listener method's parameter type
 * ({@code RideNotificationEvent} in this package) instead — the key to
 * passing a JSON event between two services with different packages.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "saferide.notifications";
    public static final String QUEUE = "notifications.queue";
    public static final String ROUTING_KEY = "ride.#";

    @Bean
    public TopicExchange notificationsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationsQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding notificationsBinding(Queue notificationsQueue,
                                        TopicExchange notificationsExchange) {
        return BindingBuilder.bind(notificationsQueue)
                .to(notificationsExchange)
                .with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        // Jackson 3 converter — Spring Boot 4 ships Jackson 3 (tools.jackson);
        // the Jackson 2 variant isn't on the classpath and fails to load.
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTypePrecedence(JacksonJavaTypeMapper.TypePrecedence.INFERRED);
        typeMapper.setTrustedPackages("*");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
