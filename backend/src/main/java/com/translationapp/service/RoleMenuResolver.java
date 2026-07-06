package com.translationapp.service;

import com.translationapp.entity.Menu;
import com.translationapp.entity.Permission;
import com.translationapp.repository.MenuRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayDeque;

/**
 * Derives role navigation menus from assigned permission codes.
 */
@Component
@RequiredArgsConstructor
public class RoleMenuResolver {

    private static final List<Map.Entry<String, String>> PATH_RULES = List.of(
            Map.entry("system:user", "/system/users"),
            Map.entry("system:role", "/system/roles"),
            Map.entry("system:permission", "/system/permissions"),
            Map.entry("system:menu", "/system/menus"),
            Map.entry("video:anime", "/video/anime"),
            Map.entry("video:short-drama", "/video/short-drama"),
            Map.entry("video:tv-series", "/video/tv-series"),
            Map.entry("video:movie", "/video/movie"),
            Map.entry("video:variety", "/video/variety"),
            Map.entry("document", "/documents"),
            Map.entry("task", "/tasks")
    );

    private final MenuRepository menuRepository;

    public Set<Menu> resolveMenus(Set<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return Set.of();
        }

        Set<String> targetPaths = new HashSet<>();
        targetPaths.add("/dashboard");

        for (Permission permission : permissions) {
            if (permission.getCode() == null || permission.getCode().isBlank()) {
                continue;
            }
            String code = permission.getCode().trim().toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> rule : PATH_RULES) {
                if (matchesPermissionPrefix(code, rule.getKey())) {
                    targetPaths.add(rule.getValue());
                }
            }
        }

        List<Menu> allMenus = menuRepository.findAll();
        Map<Long, Menu> byId = new LinkedHashMap<>();
        for (Menu menu : allMenus) {
            byId.put(menu.getId(), menu);
        }

        Set<Menu> resolved = new HashSet<>();
        for (Menu menu : allMenus) {
            if (menu.getPath() != null && targetPaths.contains(menu.getPath())) {
                resolved.add(menu);
                addAncestors(menu, byId, resolved);
            }
        }
        expandDescendants(resolved, allMenus);
        return resolved;
    }

    private static void expandDescendants(Set<Menu> resolved, List<Menu> allMenus) {
        Map<Long, List<Menu>> childrenByParent = allMenus.stream()
                .filter(m -> m.getParentId() != null)
                .collect(java.util.stream.Collectors.groupingBy(Menu::getParentId));

        Set<Long> resolvedIds = resolved.stream().map(Menu::getId).collect(java.util.stream.Collectors.toSet());
        ArrayDeque<Long> queue = new ArrayDeque<>(resolvedIds);
        while (!queue.isEmpty()) {
            Long parentId = queue.poll();
            for (Menu child : childrenByParent.getOrDefault(parentId, List.of())) {
                if (!Boolean.TRUE.equals(child.getIsVisible()) || !Boolean.TRUE.equals(child.getIsEnabled())) {
                    continue;
                }
                if (resolvedIds.add(child.getId())) {
                    resolved.add(child);
                    queue.add(child.getId());
                }
            }
        }
    }

    private static boolean matchesPermissionPrefix(String code, String prefix) {
        return code.equals(prefix) || code.startsWith(prefix + ":");
    }

    private static void addAncestors(Menu menu, Map<Long, Menu> byId, Set<Menu> resolved) {
        Long parentId = menu.getParentId();
        while (parentId != null) {
            Menu parent = byId.get(parentId);
            if (parent == null || !resolved.add(parent)) {
                break;
            }
            parentId = parent.getParentId();
        }
    }
}
