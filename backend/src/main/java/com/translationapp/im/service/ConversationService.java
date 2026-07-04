package com.translationapp.im.service;

import com.translationapp.entity.User;
import com.translationapp.im.domain.ConversationType;
import com.translationapp.im.domain.MemberRole;
import com.translationapp.im.dto.*;
import com.translationapp.im.entity.Conversation;
import com.translationapp.im.entity.ConversationMember;
import com.translationapp.im.entity.ConversationMemberId;
import com.translationapp.im.entity.Message;
import com.translationapp.im.repository.ConversationMemberRepository;
import com.translationapp.im.repository.ConversationRepository;
import com.translationapp.im.repository.MessageRepository;
import com.translationapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public List<ConversationDTO> listConversations(Long userId) {
        return conversationMemberRepository.findByIdUserId(userId).stream()
                .map(cm -> toConversationDTO(cm, userId))
                .sorted(Comparator
                        .comparing(ConversationDTO::getPinned, Comparator.nullsLast(Boolean::compareTo)).reversed()
                        .thenComparing(ConversationDTO::getLastMessageAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public ConversationDTO getConversation(Long userId, Long conversationId) {
        ensureMember(userId, conversationId);
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        ConversationMember member = conversationMemberRepository
                .findByIdConversationIdAndIdUserId(conversationId, userId).orElseThrow();
        ConversationDTO dto = mapConversation(conversation, member);
        if (conversation.getType() == ConversationType.DIRECT) {
            enrichDirectConversation(dto, conversationId, userId);
        }
        dto.setMembers(loadMembers(conversationId));
        return dto;
    }

    @Transactional
    public ConversationDTO getOrCreateDirect(Long userId, Long targetUserId) {
        if (userId.equals(targetUserId)) {
            throw new IllegalArgumentException("Cannot chat with yourself");
        }
        userRepository.findById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Optional<Conversation> existing = findDirectConversation(userId, targetUserId);
        if (existing.isPresent()) {
            return getConversation(userId, existing.get().getId());
        }

        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.DIRECT);
        conversation = conversationRepository.save(conversation);

        addMember(conversation.getId(), userId, MemberRole.MEMBER);
        addMember(conversation.getId(), targetUserId, MemberRole.MEMBER);
        return getConversation(userId, conversation.getId());
    }

    @Transactional
    public ConversationDTO createGroup(Long userId, GroupConversationCreateDTO request) {
        Conversation conversation = new Conversation();
        conversation.setType(ConversationType.GROUP);
        conversation.setTitle(request.getName());
        conversation.setOwnerId(userId);
        conversation = conversationRepository.save(conversation);

        Set<Long> memberIds = new HashSet<>(request.getMemberIds());
        memberIds.add(userId);

        for (Long memberId : memberIds) {
            MemberRole role = memberId.equals(userId) ? MemberRole.OWNER : MemberRole.MEMBER;
            addMember(conversation.getId(), memberId, role);
        }
        return getConversation(userId, conversation.getId());
    }

    @Transactional
    public ConversationDTO updateConversation(Long userId, Long conversationId, ConversationUpdateDTO request) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        ensureAdminOrOwner(userId, conversationId);
        if (request.getTitle() != null) {
            conversation.setTitle(request.getTitle());
        }
        if (request.getAvatarUrl() != null) {
            conversation.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getAnnouncement() != null) {
            conversation.setAnnouncement(request.getAnnouncement());
        }
        conversationRepository.save(conversation);
        return getConversation(userId, conversationId);
    }

    @Transactional
    public void addMembers(Long userId, Long conversationId, List<Long> memberIds) {
        ensureAdminOrOwner(userId, conversationId);
        for (Long memberId : memberIds) {
            if (conversationMemberRepository.findByIdConversationIdAndIdUserId(conversationId, memberId).isEmpty()) {
                addMember(conversationId, memberId, MemberRole.MEMBER);
            }
        }
    }

    @Transactional
    public void removeMember(Long userId, Long conversationId, Long targetUserId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        if (userId.equals(targetUserId) || isAdminOrOwner(userId, conversationId)) {
            ConversationMemberId id = new ConversationMemberId();
            id.setConversationId(conversationId);
            id.setUserId(targetUserId);
            conversationMemberRepository.deleteById(id);
            if (conversation.getOwnerId() != null && conversation.getOwnerId().equals(targetUserId)) {
                conversationMemberRepository.findByIdConversationId(conversationId).stream()
                        .findFirst()
                        .ifPresent(cm -> {
                            conversation.setOwnerId(cm.getId().getUserId());
                            conversationRepository.save(conversation);
                        });
            }
            return;
        }
        throw new IllegalArgumentException("Not authorized to remove member");
    }

    public void ensureMember(Long userId, Long conversationId) {
        if (conversationMemberRepository.findByIdConversationIdAndIdUserId(conversationId, userId).isEmpty()) {
            throw new IllegalArgumentException("Not a conversation member");
        }
    }

    public List<Long> getMemberUserIds(Long conversationId) {
        return conversationMemberRepository.findByIdConversationId(conversationId).stream()
                .map(cm -> cm.getId().getUserId())
                .toList();
    }

    private Optional<Conversation> findDirectConversation(Long userId, Long targetUserId) {
        List<ConversationMember> myConvs = conversationMemberRepository.findByIdUserId(userId);
        Set<Long> myConvIds = myConvs.stream().map(cm -> cm.getId().getConversationId()).collect(Collectors.toSet());

        return conversationMemberRepository.findByIdUserId(targetUserId).stream()
                .map(cm -> cm.getId().getConversationId())
                .filter(myConvIds::contains)
                .map(conversationRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(c -> c.getType() == ConversationType.DIRECT)
                .findFirst();
    }

    private void addMember(Long conversationId, Long userId, MemberRole role) {
        ConversationMemberId id = new ConversationMemberId();
        id.setConversationId(conversationId);
        id.setUserId(userId);
        ConversationMember member = new ConversationMember();
        member.setId(id);
        member.setRole(role);
        member.setUnreadCount(0);
        conversationMemberRepository.save(member);
    }

    private void ensureAdminOrOwner(Long userId, Long conversationId) {
        if (!isAdminOrOwner(userId, conversationId)) {
            throw new IllegalArgumentException("Not authorized");
        }
    }

    private boolean isAdminOrOwner(Long userId, Long conversationId) {
        return conversationMemberRepository.findByIdConversationIdAndIdUserId(conversationId, userId)
                .map(cm -> cm.getRole() == MemberRole.OWNER || cm.getRole() == MemberRole.ADMIN)
                .orElse(false);
    }

    private ConversationDTO toConversationDTO(ConversationMember member, Long currentUserId) {
        Conversation conversation = conversationRepository.findById(member.getId().getConversationId()).orElse(null);
        if (conversation == null) {
            return null;
        }
        ConversationDTO dto = mapConversation(conversation, member);
        if (conversation.getType() == ConversationType.DIRECT) {
            enrichDirectConversation(dto, conversation.getId(), currentUserId);
        }
        if (conversation.getLastMessageId() != null) {
            messageRepository.findById(conversation.getLastMessageId())
                    .ifPresent(msg -> dto.setLastMessagePreview(truncate(msg.getContent())));
        }
        return dto;
    }

    private ConversationDTO mapConversation(Conversation conversation, ConversationMember member) {
        ConversationDTO dto = new ConversationDTO();
        dto.setId(conversation.getId());
        dto.setType(conversation.getType());
        dto.setTitle(conversation.getTitle());
        dto.setAvatarUrl(conversation.getAvatarUrl());
        dto.setAnnouncement(conversation.getAnnouncement());
        dto.setOwnerId(conversation.getOwnerId());
        dto.setLastMessageId(conversation.getLastMessageId());
        dto.setLastMessageAt(conversation.getLastMessageAt());
        dto.setUnreadCount(member.getUnreadCount());
        dto.setPinned(member.getPinned());
        return dto;
    }

    private List<ConversationMemberDTO> loadMembers(Long conversationId) {
        return conversationMemberRepository.findByIdConversationId(conversationId).stream()
                .map(cm -> {
                    ConversationMemberDTO dto = new ConversationMemberDTO();
                    dto.setUserId(cm.getId().getUserId());
                    dto.setRole(cm.getRole());
                    dto.setJoinedAt(cm.getJoinedAt());
                    userRepository.findById(cm.getId().getUserId()).ifPresent(u -> {
                        dto.setUsername(u.getUsername());
                        dto.setRealName(u.getRealName());
                        dto.setAvatar(u.getAvatar());
                    });
                    return dto;
                }).toList();
    }

    private void enrichDirectConversation(ConversationDTO dto, Long conversationId, Long currentUserId) {
        conversationMemberRepository.findByIdConversationId(conversationId).stream()
                .map(cm -> cm.getId().getUserId())
                .filter(uid -> !uid.equals(currentUserId))
                .findFirst()
                .flatMap(userRepository::findById)
                .ifPresent(peer -> {
                    dto.setPeerUserId(peer.getId());
                    dto.setPeerUser(toUserSummary(peer));
                    dto.setTitle(displayName(peer));
                    if (peer.getAvatar() != null) {
                        dto.setAvatarUrl(peer.getAvatar());
                    }
                });
    }

    private UserSummaryDTO toUserSummary(User user) {
        UserSummaryDTO summary = new UserSummaryDTO();
        summary.setId(user.getId());
        summary.setUsername(user.getUsername());
        summary.setRealName(user.getRealName());
        summary.setAvatar(user.getAvatar());
        summary.setPhone(user.getPhone());
        return summary;
    }

    private String displayName(User user) {
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return user.getUsername();
    }

    private String truncate(String content) {
        if (content == null) {
            return null;
        }
        return content.length() > 80 ? content.substring(0, 80) + "..." : content;
    }
}
