package com.translationapp.dto;

import lombok.Data;

@Data
public class MenuUpdateRequest {
    private String name;
    private Long parentId;
    private String icon;
    private String path;
    private String componentPath;
    private Integer sortOrder;
    private Boolean isVisible;
    private Boolean isEnabled;
}