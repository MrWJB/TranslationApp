package com.translationapp.im.dto;

import lombok.Data;

@Data
public class PresignResponseDTO {
    private Long attachmentId;
    private String uploadUrl;
    private String objectKey;
    private boolean minioAvailable;
}
