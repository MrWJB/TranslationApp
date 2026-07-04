package com.translationapp.im.service;

import com.translationapp.im.config.WebrtcProperties;
import com.translationapp.im.domain.CallStatus;
import com.translationapp.im.domain.CallType;
import com.translationapp.im.domain.ImEventType;
import com.translationapp.im.domain.MessageType;
import com.translationapp.im.dto.CallCreateDTO;
import com.translationapp.im.dto.CallSessionDTO;
import com.translationapp.im.dto.ImEvent;
import com.translationapp.im.dto.SendMessageDTO;
import com.translationapp.im.entity.CallParticipant;
import com.translationapp.im.entity.CallParticipantId;
import com.translationapp.im.entity.CallSession;
import com.translationapp.im.repository.CallParticipantRepository;
import com.translationapp.im.repository.CallSessionRepository;
import com.translationapp.im.websocket.ImEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CallSignalingService {

    private final CallSessionRepository callSessionRepository;
    private final CallParticipantRepository callParticipantRepository;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final ImEventPublisher eventPublisher;
    private final WebrtcProperties webrtcProperties;

    @Transactional
    public CallSessionDTO initiateCall(Long userId, CallCreateDTO request) {
        conversationService.ensureMember(userId, request.getConversationId());

        CallSession session = new CallSession();
        session.setConversationId(request.getConversationId());
        session.setInitiatorId(userId);
        session.setType(request.getType());
        session.setStatus(CallStatus.RINGING);
        session = callSessionRepository.save(session);
        final Long callSessionId = session.getId();

        addParticipant(callSessionId, userId);
        conversationService.getMemberUserIds(request.getConversationId()).stream()
                .filter(id -> !id.equals(userId))
                .forEach(id -> addParticipant(callSessionId, id));

        CallSessionDTO dto = toDTO(session);
        Map<String, Object> payload = new HashMap<>();
        payload.put("call", dto);
        payload.put("webrtc", webrtcConfig());

        conversationService.getMemberUserIds(request.getConversationId()).stream()
                .filter(id -> !id.equals(userId))
                .forEach(id -> eventPublisher.publishToUser(id, "/queue/calls",
                        ImEvent.of(ImEventType.CALL_RINGING, payload)));

        return dto;
    }

    @Transactional
    public CallSessionDTO answerCall(Long userId, Long callId) {
        CallSession session = getCall(callId);
        ensureParticipant(session.getId(), userId);
        session.setStatus(CallStatus.ACTIVE);
        session = callSessionRepository.save(session);

        CallSessionDTO dto = toDTO(session);
        Map<String, Object> payload = new HashMap<>();
        payload.put("call", dto);
        payload.put("answeredBy", userId);
        payload.put("webrtc", webrtcConfig());

        eventPublisher.publishToUser(session.getInitiatorId(), "/queue/calls",
                ImEvent.of(ImEventType.CALL_ANSWERED, payload));
        return dto;
    }

    @Transactional
    public CallSessionDTO rejectCall(Long userId, Long callId) {
        CallSession session = getCall(callId);
        ensureParticipant(session.getId(), userId);
        session.setStatus(CallStatus.REJECTED);
        session.setEndedAt(LocalDateTime.now());
        session = callSessionRepository.save(session);

        notifyCallEnd(session, userId, ImEventType.CALL_REJECTED);
        return toDTO(session);
    }

    @Transactional
    public CallSessionDTO endCall(Long userId, Long callId) {
        CallSession session = getCall(callId);
        ensureParticipant(session.getId(), userId);
        session.setStatus(CallStatus.ENDED);
        session.setEndedAt(LocalDateTime.now());
        session = callSessionRepository.save(session);

        notifyCallEnd(session, userId, ImEventType.CALL_END);
        createSystemMessage(session);
        return toDTO(session);
    }

    public void relayOffer(Long userId, Long callId, String sdp) {
        CallSession session = getCall(callId);
        relaySignal(session, userId, ImEventType.CALL_OFFER, sdp);
    }

    public void relayAnswer(Long userId, Long callId, String sdp) {
        CallSession session = getCall(callId);
        relaySignal(session, userId, ImEventType.CALL_ANSWER, sdp);
    }

    public void relayIce(Long userId, Long callId, Object candidate) {
        CallSession session = getCall(callId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("callId", callId);
        payload.put("fromUserId", userId);
        payload.put("candidate", candidate);
        conversationService.getMemberUserIds(session.getConversationId()).stream()
                .filter(id -> !id.equals(userId))
                .forEach(id -> eventPublisher.publishToUser(id, "/queue/calls",
                        ImEvent.of(ImEventType.CALL_ICE, payload)));
    }

    private void relaySignal(CallSession session, Long userId, ImEventType type, String sdp) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("callId", session.getId());
        payload.put("fromUserId", userId);
        payload.put("sdp", sdp);
        payload.put("webrtc", webrtcConfig());
        Long target = userId.equals(session.getInitiatorId())
                ? conversationService.getMemberUserIds(session.getConversationId()).stream()
                    .filter(id -> !id.equals(userId)).findFirst().orElse(null)
                : session.getInitiatorId();
        if (target != null) {
            eventPublisher.publishToUser(target, "/queue/calls", ImEvent.of(type, payload));
        }
    }

    private void notifyCallEnd(CallSession session, Long actorId, ImEventType eventType) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("call", toDTO(session));
        payload.put("actorId", actorId);
        conversationService.getMemberUserIds(session.getConversationId()).stream()
                .filter(id -> !id.equals(actorId))
                .forEach(id -> eventPublisher.publishToUser(id, "/queue/calls", ImEvent.of(eventType, payload)));
    }

    private void createSystemMessage(CallSession session) {
        SendMessageDTO msg = new SendMessageDTO();
        msg.setConversationId(session.getConversationId());
        msg.setType(MessageType.CALL);
        msg.setContent("Call " + session.getStatus().name().toLowerCase());
        messageService.sendMessage(session.getInitiatorId(), msg);
    }

    private CallSession getCall(Long callId) {
        return callSessionRepository.findById(callId)
                .orElseThrow(() -> new IllegalArgumentException("Call not found"));
    }

    private void ensureParticipant(Long callId, Long userId) {
        CallParticipantId id = new CallParticipantId();
        id.setCallSessionId(callId);
        id.setUserId(userId);
        if (callParticipantRepository.findById(id).isEmpty()) {
            throw new IllegalArgumentException("Not a call participant");
        }
    }

    private void addParticipant(Long callId, Long userId) {
        CallParticipantId id = new CallParticipantId();
        id.setCallSessionId(callId);
        id.setUserId(userId);
        CallParticipant participant = new CallParticipant();
        participant.setId(id);
        callParticipantRepository.save(participant);
    }

    private Map<String, Object> webrtcConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("stunUrls", webrtcProperties.getStunUrls());
        config.put("turnUrls", webrtcProperties.getTurnUrls());
        config.put("turnUsername", webrtcProperties.getTurnUsername());
        config.put("turnCredential", webrtcProperties.getTurnCredential());
        return config;
    }

    private CallSessionDTO toDTO(CallSession session) {
        CallSessionDTO dto = new CallSessionDTO();
        dto.setId(session.getId());
        dto.setConversationId(session.getConversationId());
        dto.setInitiatorId(session.getInitiatorId());
        dto.setType(session.getType());
        dto.setStatus(session.getStatus());
        dto.setStartedAt(session.getStartedAt());
        dto.setEndedAt(session.getEndedAt());
        return dto;
    }
}
