package com.translationapp.im.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AttachmentCompleteDTO {
    @NotNull
    private Long attachmentId;
    private String sha256;
}
