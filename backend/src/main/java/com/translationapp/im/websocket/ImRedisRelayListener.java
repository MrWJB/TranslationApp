package com.translationapp.im.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.translationapp.im.config.ImProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ImRedisRelayListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public void onMessage(String message) {
        try {
            ImEventPublisher.RelayMessage relay = objectMapper.readValue(message, ImEventPublisher.RelayMessage.class);
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(relay.getUserId()),
                    relay.getDestination(),
                    relay.getEvent());
        } catch (Exception e) {
            log.debug("Failed to relay IM event: {}", e.getMessage());
        }
    }
}
