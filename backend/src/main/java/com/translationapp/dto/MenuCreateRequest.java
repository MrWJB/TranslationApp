package com.translationapp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MenuCreateRequest {
    @NotBlank(message = "菜单名称不能为空")
    private String name;

    private Long parentId;
    private String icon;
    private String path;
    private String componentPath;
    private Integer sortOrder;
    private Boolean isVisible;
    private Boolean isEnabled;
}