package com.translationapp.dto;

import lombok.Data;

@Data
public class PermissionUpdateRequest {
    private String name;
    private String description;
    private String moduleName;
    private Long parentId;
    private Integer sortOrder;
}