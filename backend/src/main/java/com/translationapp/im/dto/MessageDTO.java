package com.translationapp.im.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.translationapp.im.domain.MessageType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageDTO {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private MessageType type;
    private String content;
    private Long attachmentId;
    private Long replyToId;
    private String clientMsgId;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
