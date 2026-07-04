package com.translationapp.im.websocket;

import com.translationapp.im.dto.SendMessageDTO;
import com.translationapp.im.security.ImUserPrincipal;
import com.translationapp.im.service.CallSignalingService;
import com.translationapp.im.service.MessageService;
import com.translationapp.im.service.PresenceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class ImStompController {

    private final MessageService messageService;
    private final PresenceService presenceService;
    private final CallSignalingService callSignalingService;

    @MessageMapping("/im/send")
    public void send(@Payload SendMessageDTO payload, Principal principal) {
        messageService.sendMessage(requireUserId(principal), payload);
    }

    @MessageMapping("/im/read")
    public void read(@Payload ReadPayload payload, Principal principal) {
        messageService.markRead(requireUserId(principal), payload.getConversationId(), payload.getMessageId());
    }

    @MessageMapping("/im/typing")
    public void typing(@Payload TypingPayload payload, Principal principal) {
        messageService.broadcastTyping(requireUserId(principal), payload.getConversationId(), payload.isTyping());
    }

    @MessageMapping("/im/presence/heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor accessor) {
        Long userId = resolveUserId(accessor.getUser());
        if (userId != null) {
            presenceService.heartbeat(userId);
        }
    }

    @MessageMapping("/im/call/offer")
    public void callOffer(@Payload CallSdpPayload payload, Principal principal) {
        callSignalingService.relayOffer(requireUserId(principal), payload.getCallId(), payload.getSdp());
    }

    @MessageMapping("/im/call/answer")
    public void callAnswer(@Payload CallSdpPayload payload, Principal principal) {
        callSignalingService.relayAnswer(requireUserId(principal), payload.getCallId(), payload.getSdp());
    }

    @MessageMapping("/im/call/ice")
    public void callIce(@Payload CallIcePayload payload, Principal principal) {
        callSignalingService.relayIce(requireUserId(principal), payload.getCallId(), payload.getCandidate());
    }

    private Long requireUserId(Principal principal) {
        Long userId = resolveUserId(principal);
        if (userId == null) {
            throw new IllegalArgumentException("Not authenticated");
        }
        return userId;
    }

    private Long resolveUserId(Principal principal) {
        if (principal instanceof ImUserPrincipal imUser) {
            return imUser.getUserId();
        }
        if (principal instanceof org.springframework.security.core.Authentication auth
                && auth.getPrincipal() instanceof ImUserPrincipal imUser) {
            return imUser.getUserId();
        }
        if (principal != null) {
            try {
                return Long.parseLong(principal.getName());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @Data
    public static class ReadPayload {
        private Long conversationId;
        private Long messageId;
    }

    @Data
    public static class TypingPayload {
        private Long conversationId;
        private boolean typing;
    }

    @Data
    public static class CallSdpPayload {
        private Long callId;
        private String sdp;
    }

    @Data
    public static class CallIcePayload {
        private Long callId;
        private Map<String, Object> candidate;
    }
}
