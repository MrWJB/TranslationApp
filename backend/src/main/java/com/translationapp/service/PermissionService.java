package com.translationapp.service;

import com.translationapp.dto.*;
import com.translationapp.entity.Permission;
import com.translationapp.entity.Role;
import com.translationapp.repository.PermissionRepository;
import com.translationapp.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private static final String ADMIN_ROLE_NAME = "ADMIN";

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    public List<PermissionDTO> findAll() {
        return permissionRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<PermissionDTO> findTree() {
        List<Permission> allPermissions = permissionRepository.findAll();

        // Get root permissions (no parent)
        List<Permission> rootPermissions = allPermissions.stream()
                .filter(p -> p.getParentId() == null)
                .sorted((a, b) -> (a.getSortOrder() != null ? a.getSortOrder() : 0) -
                                  (b.getSortOrder() != null ? b.getSortOrder() : 0))
                .collect(Collectors.toList());

        // Build tree
        Map<Long, List<Permission>> childrenMap = allPermissions.stream()
                .filter(p -> p.getParentId() != null)
                .collect(Collectors.groupingBy(Permission::getParentId));

        return rootPermissions.stream()
                .map(p -> toDTOWithChildren(p, childrenMap))
                .collect(Collectors.toList());
    }

    public PermissionDTO findById(Long id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("权限不存在"));
        return toDTO(permission);
    }

    @Transactional
    public PermissionDTO create(PermissionCreateRequest request) {
        if (permissionRepository.existsByCode(request.getCode())) {
            throw new RuntimeException("权限编码已存在");
        }

        Permission permission = new Permission();
        permission.setCode(request.getCode());
        permission.setName(request.getName());
        permission.setDescription(request.getDescription());
        permission.setModuleName(request.getModuleName());
        permission.setParentId(request.getParentId());
        permission.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);

        return toDTO(permissionRepository.save(permission));
    }

    @Transactional
    public PermissionDTO update(Long id, PermissionUpdateRequest request) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("权限不存在"));

        permission.setName(request.getName());
        permission.setDescription(request.getDescription());
        permission.setModuleName(request.getModuleName());
        permission.setParentId(request.getParentId());
        permission.setSortOrder(request.getSortOrder());

        return toDTO(permissionRepository.save(permission));
    }

    @Transactional
    public void delete(Long id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("权限不存在"));

        // Check if has children
        List<Permission> children = permissionRepository.findByParentId(id);
        if (!children.isEmpty()) {
            throw new RuntimeException("存在子权限，不能删除");
        }

        permissionRepository.delete(permission);
    }

    /** 启动时及新增权限后，确保 ADMIN 拥有全部权限。 */
    @Transactional
    public void syncAdminRolePermissions() {
        roleRepository.findByName(ADMIN_ROLE_NAME).ifPresent(role -> {
            Set<Permission> allPermissions = new HashSet<>(permissionRepository.findAll());
            if (!role.getPermissions().equals(allPermissions)) {
                role.setPermissions(allPermissions);
                roleRepository.save(role);
            }
        });
    }

    private PermissionDTO toDTO(Permission permission) {
        PermissionDTO dto = new PermissionDTO();
        dto.setId(permission.getId());
        dto.setCode(permission.getCode());
        dto.setName(permission.getName());
        dto.setDescription(permission.getDescription());
        dto.setModuleName(permission.getModuleName());
        dto.setParentId(permission.getParentId());
        dto.setSortOrder(permission.getSortOrder());
        return dto;
    }

    private PermissionDTO toDTOWithChildren(Permission permission, Map<Long, List<Permission>> childrenMap) {
        PermissionDTO dto = toDTO(permission);

        List<Permission> children = childrenMap.getOrDefault(permission.getId(), new ArrayList<>());
        if (!children.isEmpty()) {
            dto.setChildren(children.stream()
                    .sorted((a, b) -> (a.getSortOrder() != null ? a.getSortOrder() : 0) -
                                      (b.getSortOrder() != null ? b.getSortOrder() : 0))
                    .map(c -> toDTOWithChildren(c, childrenMap))
                    .collect(Collectors.toList()));
        }

        return dto;
    }
}