package com.translationapp.im.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DepartmentCreateDTO {
    @NotBlank
    private String name;
    private Long parentId;
    private Integer sortOrder;
    private Long leaderUserId;
}
