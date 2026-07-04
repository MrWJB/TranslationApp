package com.translationapp.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PresignRequestDTO {
    @NotBlank
    private String fileName;
    @NotBlank
    private String mimeType;
    @NotNull
    private Long size;
}
