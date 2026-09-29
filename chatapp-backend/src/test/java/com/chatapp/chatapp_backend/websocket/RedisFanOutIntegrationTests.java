package com.chatapp.chatapp_backend.websocket;

import com.chatapp.chatapp_backend.config.RedisConfig;
import com.chatapp.chatapp_backend.dto.MessageResponse;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class RedisFanOutIntegrationTests {
    @Test
    void realRedisDeliversToTwoIndependentInstanceSubscribersExactlyOnce() throws Exception {
        var factory = new LettuceConnectionFactory("localhost",
                Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "6379")));
        factory.afterPropertiesSet();
        factory.start();
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var firstClient = mock(SimpMessagingTemplate.class);
        var secondClient = mock(SimpMessagingTemplate.class);
        var config = new RedisConfig();
        var first = config.redisMessageListenerContainer(factory, new RedisMessageSubscriber(firstClient, mapper));
        var second = config.redisMessageListenerContainer(factory, new RedisMessageSubscriber(secondClient, mapper));
        try {
            first.afterPropertiesSet();
            second.afterPropertiesSet();
            first.start();
            second.start();
            var redis = new StringRedisTemplate(factory);
            var beans = new StaticListableBeanFactory();
            beans.addBean("redis", redis);
            var broadcaster = new MessageBroadcaster(firstClient, beans.getBeanProvider(StringRedisTemplate.class), mapper);
            var payload = MessageResponse.builder().id(1L).channelId(91L).content("Olá de outra instância 🚀")
                    .attachmentUrl("https://chat.example/uploads/1.txt").attachmentType("FILE")
                    .createdAt(LocalDateTime.now()).build();
            broadcaster.onMessageCreated(new MessageCreatedEvent(payload));
            verify(firstClient, timeout(5000).times(1)).convertAndSend("/topic/channel/91", payload);
            verify(secondClient, timeout(5000).times(1)).convertAndSend("/topic/channel/91", payload);
            verify(firstClient, after(200).times(1)).convertAndSend("/topic/channel/91", payload);
            verifyNoMoreInteractions(firstClient, secondClient);
        } finally {
            first.destroy();
            second.destroy();
            factory.destroy();
        }
    }
}
