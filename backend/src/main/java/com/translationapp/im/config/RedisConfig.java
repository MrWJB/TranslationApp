package com.translationapp.im.config;

import com.translationapp.im.websocket.ImRedisRelayListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    private final ImProperties imProperties;

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter imEventListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        if (imProperties.getRedisRelay().isEnabled()) {
            container.addMessageListener(
                    imEventListenerAdapter,
                    new PatternTopic(imProperties.getRedisRelay().getChannel()));
        }
        return container;
    }

    @Bean
    public MessageListenerAdapter imEventListenerAdapter(ImRedisRelayListener listener) {
        return new MessageListenerAdapter(listener, "onMessage");
    }
}
