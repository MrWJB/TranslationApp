package com.translationapp.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class GroupConversationCreateDTO {
    @NotBlank
    private String name;
    @NotEmpty
    private List<Long> memberIds;
}
