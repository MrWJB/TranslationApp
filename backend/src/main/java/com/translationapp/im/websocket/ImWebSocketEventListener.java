package com.translationapp.im.websocket;

import com.translationapp.im.security.ImUserPrincipal;
import com.translationapp.im.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * WebSocket 连接事件监听器，用于同步用户在线状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImWebSocketEventListener {

    private final PresenceService presenceService;

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = resolveUserId(accessor.getUser());
        if (userId != null) {
            presenceService.setOnline(userId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = resolveUserId(accessor.getUser());
        if (userId != null) {
            presenceService.setOffline(userId);
        }
    }

    private Long resolveUserId(Principal principal) {
        if (principal instanceof ImUserPrincipal imUser) {
            return imUser.getUserId();
        }
        if (principal != null) {
            try {
                return Long.parseLong(principal.getName());
            } catch (NumberFormatException e) {
                log.debug("Unable to parse principal name as user id: {}", principal.getName());
            }
        }
        return null;
    }
}
