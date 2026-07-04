package com.translationapp.im.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DirectConversationCreateDTO {
    @NotNull
    private Long targetUserId;
}
