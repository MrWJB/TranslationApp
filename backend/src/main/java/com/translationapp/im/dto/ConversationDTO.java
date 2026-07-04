package com.translationapp.im.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.translationapp.im.domain.ConversationType;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ConversationDTO {
    private Long id;
    private ConversationType type;
    private String title;
    private String avatarUrl;
    private String announcement;
    private Long ownerId;
    private Long lastMessageId;
    private String lastMessagePreview;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastMessageAt;
    private Integer unreadCount;
    private Boolean pinned;
    private List<ConversationMemberDTO> members;
    /** For DIRECT chats: the other participant's user id (relative to the requesting user). */
    private Long peerUserId;
    /** For DIRECT chats: summary of the other participant. */
    private UserSummaryDTO peerUser;
}
