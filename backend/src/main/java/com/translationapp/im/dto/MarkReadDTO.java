package com.translationapp.im.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MarkReadDTO {
    @NotNull
    private Long lastMessageId;
}
