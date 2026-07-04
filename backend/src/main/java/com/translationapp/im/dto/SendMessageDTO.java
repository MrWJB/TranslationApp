package com.translationapp.im.dto;

import com.translationapp.im.domain.MessageType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendMessageDTO {
    @NotNull
    private Long conversationId;
    @NotNull
    private MessageType type;
    private String content;
    private Long attachmentId;
    private Long replyToId;
    private String clientMsgId;
}
