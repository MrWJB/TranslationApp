package com.translationapp.dto;

import lombok.Data;
import java.util.List;

@Data
public class PermissionDTO {
    private Long id;
    private String code;
    private String name;
    private String description;
    private String moduleName;
    private Long parentId;
    private Integer sortOrder;
    private List<PermissionDTO> children;
}