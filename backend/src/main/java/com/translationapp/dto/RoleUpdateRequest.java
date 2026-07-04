package com.translationapp.dto;

import lombok.Data;
import java.util.Set;

@Data
public class RoleUpdateRequest {
    private String name;
    private String description;
    private Set<Long> permissionIds;
    private Set<Long> menuIds;
}