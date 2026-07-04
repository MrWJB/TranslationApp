package com.translationapp.im.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.translationapp.im.config.ImProperties;
import com.translationapp.im.dto.ImEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ImProperties imProperties;
    private final ObjectMapper objectMapper;

    public void publishToUser(Long userId, String destination, ImEvent event) {
        if (userId == null) {
            return;
        }
        String userName = String.valueOf(userId);
        if (imProperties.getRedisRelay().isEnabled()) {
            try {
                RelayMessage relay = new RelayMessage(userId, destination, event);
                stringRedisTemplate.convertAndSend(
                        imProperties.getRedisRelay().getChannel(),
                        objectMapper.writeValueAsString(relay));
            } catch (Exception e) {
                log.warn("Redis relay failed, delivering locally: {}", e.getMessage());
                messagingTemplate.convertAndSendToUser(userName, destination, event);
            }
            return;
        }
        messagingTemplate.convertAndSendToUser(userName, destination, event);
    }

    public void publishToConversation(Long conversationId, ImEvent event) {
        messagingTemplate.convertAndSend("/topic/conversation." + conversationId, event);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RelayMessage {
        private Long userId;
        private String destination;
        private ImEvent event;
    }
}
