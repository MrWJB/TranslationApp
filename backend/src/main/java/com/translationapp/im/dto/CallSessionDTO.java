package com.translationapp.im.dto;

import com.translationapp.im.domain.CallStatus;
import com.translationapp.im.domain.CallType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CallSessionDTO {
    private Long id;
    private Long conversationId;
    private Long initiatorId;
    private CallType type;
    private CallStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
