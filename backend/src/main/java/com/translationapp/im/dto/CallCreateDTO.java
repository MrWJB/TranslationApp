package com.translationapp.im.dto;

import com.translationapp.im.domain.CallType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CallCreateDTO {
    @NotNull
    private Long conversationId;
    @NotNull
    private CallType type;
}
