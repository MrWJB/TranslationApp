package com.translationapp.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Set;

@Data
public class RoleCreateRequest {
    @NotBlank(message = "角色名称不能为空")
    private String name;
    private String description;
    private Set<Long> permissionIds;
    private Set<Long> menuIds;
}