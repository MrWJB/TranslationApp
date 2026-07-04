package com.translationapp.service;

import com.translationapp.dto.*;
import com.translationapp.entity.Menu;
import com.translationapp.entity.Permission;
import com.translationapp.entity.Role;
import com.translationapp.repository.MenuRepository;
import com.translationapp.repository.PermissionRepository;
import com.translationapp.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final MenuRepository menuRepository;

    public List<RoleDTO> findAll() {
        return roleRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public RoleDTO findById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("角色不存在"));
        return toDTO(role);
    }

    @Transactional
    public RoleDTO create(RoleCreateRequest request) {
        if (roleRepository.existsByName(request.getName())) {
            throw new RuntimeException("角色名称已存在");
        }

        Role role = new Role();
        role.setName(request.getName());
        role.setDescription(request.getDescription());
        role.setIsSystem(false);

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(request.getPermissionIds()));
            role.setPermissions(permissions);
        }

        if (request.getMenuIds() != null && !request.getMenuIds().isEmpty()) {
            Set<Menu> menus = new HashSet<>(menuRepository.findAllById(request.getMenuIds()));
            role.setMenus(menus);
        }

        return toDTO(roleRepository.save(role));
    }

    @Transactional
    public RoleDTO update(Long id, RoleUpdateRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("角色不存在"));

        if (role.getIsSystem()) {
            throw new RuntimeException("系统角色不能修改");
        }

        if (request.getName() != null && !request.getName().equals(role.getName())) {
            if (roleRepository.existsByName(request.getName())) {
                throw new RuntimeException("角色名称已存在");
            }
            role.setName(request.getName());
        }

        role.setDescription(request.getDescription());

        if (request.getPermissionIds() != null) {
            Set<Permission> permissions = new HashSet<>(permissionRepository.findAllById(request.getPermissionIds()));
            role.setPermissions(permissions);
        }

        if (request.getMenuIds() != null) {
            Set<Menu> menus = new HashSet<>(menuRepository.findAllById(request.getMenuIds()));
            role.setMenus(menus);
        }

        return toDTO(roleRepository.save(role));
    }

    @Transactional
    public void delete(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("角色不存在"));

        if (role.getIsSystem()) {
            throw new RuntimeException("系统角色不能删除");
        }

        roleRepository.delete(role);
    }

    private RoleDTO toDTO(Role role) {
        RoleDTO dto = new RoleDTO();
        dto.setId(role.getId());
        dto.setName(role.getName());
        dto.setDescription(role.getDescription());
        dto.setIsSystem(role.getIsSystem());
        dto.setPermissionIds(role.getPermissions().stream().map(Permission::getId).collect(Collectors.toSet()));
        dto.setMenuIds(role.getMenus().stream().map(Menu::getId).collect(Collectors.toSet()));
        return dto;
    }
}