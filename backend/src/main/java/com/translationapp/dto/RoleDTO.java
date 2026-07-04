package com.translationapp.dto;

import lombok.Data;
import java.util.Set;

@Data
public class RoleDTO {
    private Long id;
    private String name;
    private String description;
    private Boolean isSystem;
    private Set<Long> permissionIds;
    private Set<Long> menuIds;
}