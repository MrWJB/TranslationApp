package com.translationapp.dto;

import lombok.Data;
import java.util.List;

@Data
public class MenuDTO {
    private Long id;
    private String name;
    private Long parentId;
    private String icon;
    private String path;
    private String componentPath;
    private Integer sortOrder;
    private Boolean isVisible;
    private Boolean isEnabled;
    private List<MenuDTO> children;
}