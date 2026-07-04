package com.translationapp.im.service;

import com.translationapp.entity.User;
import com.translationapp.im.config.ImProperties;
import com.translationapp.im.domain.ConversationType;
import com.translationapp.im.domain.ImEventType;
import com.translationapp.im.domain.MessageType;
import com.translationapp.im.dto.ImEvent;
import com.translationapp.im.dto.MessageDTO;
import com.translationapp.im.dto.SendMessageDTO;
import com.translationapp.im.entity.*;
import com.translationapp.im.repository.*;
import com.translationapp.im.websocket.ImEventPublisher;
import com.translationapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageReadRepository messageReadRepository;
    private final ConversationService conversationService;
    private final UserRepository userRepository;
    private final ImProperties imProperties;
    private final ImEventPublisher eventPublisher;

    public List<MessageDTO> getHistory(Long userId, Long conversationId, Long beforeMessageId, Integer limit) {
        conversationService.ensureMember(userId, conversationId);
        int pageSize = limit != null ? limit : imProperties.getMessage().getHistoryPageSize();

        List<Message> messages;
        if (beforeMessageId != null) {
            Message before = messageRepository.findById(beforeMessageId)
                    .orElseThrow(() -> new IllegalArgumentException("Message not found"));
            messages = messageRepository.findByConversationIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                    conversationId, before.getCreatedAt(), PageRequest.of(0, pageSize));
        } else {
            messages = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                    conversationId, PageRequest.of(0, pageSize));
        }
        return messages.stream().map(this::toDTO).toList();
    }

    public List<MessageDTO> search(Long userId, String query, Long conversationId) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        if (conversationId != null) {
            conversationService.ensureMember(userId, conversationId);
        } else {
            conversationId = null;
        }
        return messageRepository.searchFullText(query.trim(), conversationId, 50).stream()
                .filter(m -> conversationMemberRepository
                        .findByIdConversationIdAndIdUserId(m.getConversationId(), userId).isPresent())
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public MessageDTO sendMessage(Long senderId, SendMessageDTO request) {
        conversationService.ensureMember(senderId, request.getConversationId());

        if (request.getClientMsgId() != null) {
            Optional<Message> existing = messageRepository.findByConversationIdAndClientMsgId(
                    request.getConversationId(), request.getClientMsgId());
            if (existing.isPresent()) {
                return toDTO(existing.get());
            }
        }

        String content = request.getContent();
        if (content != null && content.length() > imProperties.getMessage().getMaxLength()) {
            throw new IllegalArgumentException("Message too long");
        }
        if (request.getType() == MessageType.TEXT && content != null) {
            content = HtmlUtils.htmlEscape(content);
        }

        Message message = new Message();
        message.setConversationId(request.getConversationId());
        message.setSenderId(senderId);
        message.setType(request.getType());
        message.setContent(content);
        message.setAttachmentId(request.getAttachmentId());
        message.setReplyToId(request.getReplyToId());
        message.setClientMsgId(request.getClientMsgId());
        message = messageRepository.save(message);

        Conversation conversation = conversationRepository.findById(request.getConversationId()).orElseThrow();
        conversation.setLastMessageId(message.getId());
        conversation.setLastMessageAt(message.getCreatedAt());
        conversationRepository.save(conversation);

        incrementUnreadForOthers(request.getConversationId(), senderId);

        MessageDTO dto = toDTO(message);
        broadcastNewMessage(request.getConversationId(), senderId, dto);
        if (request.getClientMsgId() != null) {
            Map<String, Object> ack = new HashMap<>();
            ack.put("clientMsgId", request.getClientMsgId());
            ack.put("messageId", message.getId());
            eventPublisher.publishToUser(senderId, "/queue/messages", ImEvent.of(ImEventType.MESSAGE_ACK, ack));
        }
        return dto;
    }

    @Transactional
    public void markRead(Long userId, Long conversationId, Long messageId) {
        conversationService.ensureMember(userId, conversationId);
        ConversationMember member = conversationMemberRepository
                .findByIdConversationIdAndIdUserId(conversationId, userId).orElseThrow();
        member.setLastReadMessageId(messageId);
        member.setUnreadCount(0);
        conversationMemberRepository.save(member);

        MessageReadId readId = new MessageReadId();
        readId.setMessageId(messageId);
        readId.setUserId(userId);
        if (messageReadRepository.findById(readId).isEmpty()) {
            MessageRead read = new MessageRead();
            read.setId(readId);
            messageReadRepository.save(read);
        }

        Conversation conversation = conversationRepository.findById(conversationId).orElseThrow();
        if (conversation.getType() == ConversationType.DIRECT) {
            messageRepository.findById(messageId).ifPresent(msg -> {
                if (!msg.getSenderId().equals(userId)) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("conversationId", conversationId);
                    payload.put("messageId", messageId);
                    payload.put("readerId", userId);
                    eventPublisher.publishToUser(msg.getSenderId(), "/queue/messages",
                            ImEvent.of(ImEventType.MESSAGE_READ, payload));
                }
            });
        }
    }

    public void broadcastTyping(Long userId, Long conversationId, boolean typing) {
        conversationService.ensureMember(userId, conversationId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", conversationId);
        payload.put("userId", userId);
        payload.put("typing", typing);
        conversationService.getMemberUserIds(conversationId).stream()
                .filter(id -> !id.equals(userId))
                .forEach(id -> eventPublisher.publishToUser(id, "/queue/messages",
                        ImEvent.of(ImEventType.TYPING, payload)));
    }

    private void incrementUnreadForOthers(Long conversationId, Long senderId) {
        conversationMemberRepository.findByIdConversationId(conversationId).forEach(cm -> {
            if (!cm.getId().getUserId().equals(senderId)) {
                cm.setUnreadCount(cm.getUnreadCount() + 1);
                conversationMemberRepository.save(cm);
            }
        });
    }

    private void broadcastNewMessage(Long conversationId, Long senderId, MessageDTO dto) {
        ImEvent event = ImEvent.of(ImEventType.MESSAGE_NEW, dto);
        conversationService.getMemberUserIds(conversationId)
                .forEach(id -> eventPublisher.publishToUser(id, "/queue/messages", event));
        eventPublisher.publishToConversation(conversationId, event);
    }

    private MessageDTO toDTO(Message message) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setConversationId(message.getConversationId());
        dto.setSenderId(message.getSenderId());
        dto.setType(message.getType());
        dto.setContent(message.getContent());
        dto.setAttachmentId(message.getAttachmentId());
        dto.setReplyToId(message.getReplyToId());
        dto.setClientMsgId(message.getClientMsgId());
        dto.setCreatedAt(message.getCreatedAt());
        userRepository.findById(message.getSenderId()).ifPresent(u -> {
            dto.setSenderName(u.getRealName() != null ? u.getRealName() : u.getUsername());
            dto.setSenderAvatar(u.getAvatar());
        });
        return dto;
    }
}
