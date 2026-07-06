package com.translationapp.service;

import com.translationapp.dto.*;
import com.translationapp.entity.Menu;
import com.translationapp.entity.Role;
import com.translationapp.entity.User;
import com.translationapp.repository.MenuRepository;
import com.translationapp.repository.RoleRepository;
import com.translationapp.repository.UserRepository;
import com.translationapp.util.SiteKeyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuService {
    private static final Map<String, String> KNOWN_CATEGORY_NAMES = Map.of(
            "java", "Java",
            "spring", "Spring",
            "spring-boot", "Spring Boot",
            "spring-cloud", "Spring Cloud",
            "spring-mvc", "Spring Mvc",
            "mysql", "Mysql",
            "oracle", "Oracle"
    );

    private static final String ADMIN_ROLE_NAME = "ADMIN";

    private final MenuRepository menuRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final RoleMenuResolver roleMenuResolver;

    public List<MenuDTO> findAll() {
        return menuRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public List<MenuDTO> findTree() {
        List<Menu> allMenus = menuRepository.findAll();

        // Get root menus (no parent)
        List<Menu> rootMenus = allMenus.stream()
                .filter(m -> m.getParentId() == null)
                .sorted((a, b) -> (a.getSortOrder() != null ? a.getSortOrder() : 0) -
                                  (b.getSortOrder() != null ? b.getSortOrder() : 0))
                .collect(Collectors.toList());

        // Build tree
        Map<Long, List<Menu>> childrenMap = allMenus.stream()
                .filter(m -> m.getParentId() != null)
                .collect(Collectors.groupingBy(Menu::getParentId));

        return rootMenus.stream()
                .map(m -> toDTOWithChildren(m, childrenMap))
                .collect(Collectors.toList());
    }

    /**
     * 返回当前用户角色可访问的菜单树，按 sortOrder 排序。
     * 管理员返回全部可见菜单；其他角色合并 role_menus、权限推导菜单，并展开祖先/子孙节点。
     */
    @Transactional(readOnly = true)
    public List<MenuDTO> findMenusForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        if (user.getRoles().stream().anyMatch(role -> ADMIN_ROLE_NAME.equals(role.getName()))) {
            return findVisibleTree();
        }

        List<Menu> allMenus = menuRepository.findAll();
        Map<Long, Menu> byId = allMenus.stream()
                .collect(Collectors.toMap(Menu::getId, m -> m));
        Map<Long, List<Menu>> childrenByParent = allMenus.stream()
                .filter(menu -> menu.getParentId() != null)
                .collect(Collectors.groupingBy(Menu::getParentId));

        Set<Long> allowedIds = collectAllowedMenuIds(user);
        if (allowedIds.isEmpty()) {
            return List.of();
        }

        Set<Long> expandedIds = expandMenuClosure(allowedIds, byId, childrenByParent);
        List<Menu> accessible = allMenus.stream()
                .filter(menu -> expandedIds.contains(menu.getId()))
                .filter(this::isAccessibleMenu)
                .collect(Collectors.toList());

        return buildTreeFromMenuList(accessible);
    }

    public List<MenuDTO> findVisibleTree() {
        List<Menu> visibleMenus = menuRepository.findByIsVisibleTrueOrderBySortOrder();

        // Get root menus
        List<Menu> rootMenus = visibleMenus.stream()
                .filter(m -> m.getParentId() == null)
                .sorted((a, b) -> (a.getSortOrder() != null ? a.getSortOrder() : 0) -
                                  (b.getSortOrder() != null ? b.getSortOrder() : 0))
                .collect(Collectors.toList());

        // Build tree
        Map<Long, List<Menu>> childrenMap = visibleMenus.stream()
                .filter(m -> m.getParentId() != null)
                .collect(Collectors.groupingBy(Menu::getParentId));

        return rootMenus.stream()
                .map(m -> toDTOWithChildren(m, childrenMap))
                .collect(Collectors.toList());
    }

    public MenuDTO findById(Long id) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("菜单不存在"));
        return toDTO(menu);
    }

    @Transactional
    public MenuDTO create(MenuCreateRequest request) {
        Menu menu = new Menu();
        menu.setName(request.getName());
        menu.setParentId(request.getParentId());
        menu.setIcon(request.getIcon());
        menu.setPath(request.getPath());
        menu.setComponentPath(request.getComponentPath());
        menu.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        menu.setIsVisible(request.getIsVisible() != null ? request.getIsVisible() : true);
        menu.setIsEnabled(request.getIsEnabled() != null ? request.getIsEnabled() : true);

        Menu saved = menuRepository.save(menu);
        assignMenuToDefaultRoles(saved);
        return toDTO(saved);
    }

    @Transactional
    public MenuDTO update(Long id, MenuUpdateRequest request) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("菜单不存在"));

        menu.setName(request.getName());
        menu.setParentId(request.getParentId());
        menu.setIcon(request.getIcon());
        menu.setPath(request.getPath());
        menu.setComponentPath(request.getComponentPath());
        menu.setSortOrder(request.getSortOrder());
        menu.setIsVisible(request.getIsVisible());
        menu.setIsEnabled(request.getIsEnabled());

        return toDTO(menuRepository.save(menu));
    }

    @Transactional
    public void delete(Long id) {
        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("菜单不存在"));

        // Check if has children
        List<Menu> children = menuRepository.findByParentId(id);
        if (!children.isEmpty()) {
            throw new RuntimeException("存在子菜单，不能删除");
        }

        menuRepository.delete(menu);
    }

    public DocumentMenuMatchDTO matchDocumentCategoryMenu(String url, String categoryHint, String profileName) {
        String normalizedCategory = resolveNormalizedCategory(url, categoryHint);
        String siteKey = SiteKeyUtil.deriveSiteKey(url);
        String menuPath = "/documents/" + normalizedCategory;

        DocumentMenuMatchDTO result = new DocumentMenuMatchDTO();
        result.setNormalizedCategory(normalizedCategory);
        result.setSiteKey(siteKey);
        result.setMenuPath(menuPath);
        result.setSuggestedName(suggestMenuDisplayName(normalizedCategory, profileName));

        if (KNOWN_CATEGORY_NAMES.containsKey(normalizedCategory)) {
            result.setMatched(true);
            menuRepository.findByPath(menuPath).ifPresent(menu -> result.setMenu(toDTO(menu)));
            return result;
        }

        menuRepository.findByPath(menuPath).ifPresentOrElse(menu -> {
            result.setMatched(true);
            result.setMenu(toDTO(menu));
        }, () -> result.setMatched(false));

        return result;
    }

    @Transactional
    public MenuDTO ensureDocumentCategoryMenu(String menuPath, String name, String icon) {
        Optional<Menu> existing = menuRepository.findByPath(menuPath);
        if (existing.isPresent()) {
            assignMenuToDefaultRoles(existing.get());
            return toDTO(existing.get());
        }

        Menu parent = menuRepository.findByPath("/documents")
                .orElseThrow(() -> new RuntimeException("文档列表菜单不存在"));

        int nextSort = menuRepository.findByParentId(parent.getId()).stream()
                .mapToInt(m -> m.getSortOrder() != null ? m.getSortOrder() : 0)
                .max()
                .orElse(0) + 1;

        Menu menu = new Menu();
        menu.setName(name);
        menu.setParentId(parent.getId());
        menu.setPath(menuPath);
        menu.setIcon(icon != null && !icon.isBlank() ? icon : "Document");
        menu.setSortOrder(nextSort);
        menu.setIsVisible(true);
        menu.setIsEnabled(true);

        Menu saved = menuRepository.save(menu);
        assignMenuToDefaultRoles(saved);
        return toDTO(saved);
    }

    public List<MenuDTO> findDocumentCategoryMenus() {
        return menuRepository.findByPath("/documents")
                .map(parent -> menuRepository.findByParentId(parent.getId()).stream()
                        .filter(m -> m.getPath() != null && m.getPath().startsWith("/documents/")
                                && !m.getPath().contains(":"))
                        .sorted((a, b) -> Integer.compare(
                                a.getSortOrder() != null ? a.getSortOrder() : 0,
                                b.getSortOrder() != null ? b.getSortOrder() : 0))
                        .map(this::toDTO)
                        .collect(Collectors.toList()))
                .orElse(List.of());
    }

    private String resolveNormalizedCategory(String url, String categoryHint) {
        if (categoryHint != null && !categoryHint.isBlank()) {
            String hint = categoryHint.trim();
            if (KNOWN_CATEGORY_NAMES.containsKey(hint)) {
                return hint;
            }
            String fromHintUrl = SiteKeyUtil.deriveDisplayCategory("https://example.com/" + hint + "/");
            if (KNOWN_CATEGORY_NAMES.containsKey(fromHintUrl)) {
                return fromHintUrl;
            }
        }
        return SiteKeyUtil.deriveDisplayCategory(url);
    }

    private String suggestMenuDisplayName(String normalizedCategory, String profileName) {
        if (profileName != null && !profileName.isBlank()) {
            return profileName.trim();
        }
        if (KNOWN_CATEGORY_NAMES.containsKey(normalizedCategory)) {
            return KNOWN_CATEGORY_NAMES.get(normalizedCategory);
        }
        return humanizeSiteKey(normalizedCategory);
    }

    private String humanizeSiteKey(String siteKey) {
        if (siteKey == null || siteKey.isBlank()) {
            return "文档";
        }
        String[] parts = siteKey.split("-");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            if (part.matches("\\d+(\\.\\d+)*")) {
                sb.append(part);
            } else if (part.length() <= 3) {
                sb.append(part.toUpperCase());
            } else {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * 新建菜单时同步到默认系统角色，避免 role_menus 与 menus 表脱节。
     */
    void assignMenuToDefaultRoles(Menu menu) {
        roleRepository.findByName(ADMIN_ROLE_NAME).ifPresent(role -> {
            if (role.getMenus().add(menu)) {
                roleRepository.save(role);
            }
        });
        roleRepository.findByName("USER").ifPresent(role -> {
            if (shouldAssignMenuToUserRole(menu) && role.getMenus().add(menu)) {
                roleRepository.save(role);
            }
        });
    }

    /**
     * 启动或修复时，将 ADMIN 角色菜单与当前全部菜单对齐。
     */
    @Transactional
    public void syncAdminRoleMenus() {
        roleRepository.findByName(ADMIN_ROLE_NAME).ifPresent(role -> {
            Set<Menu> allMenus = new HashSet<>(menuRepository.findAll());
            if (!role.getMenus().equals(allMenus)) {
                role.setMenus(allMenus);
                roleRepository.save(role);
            }
        });
    }

    private Set<Long> collectAllowedMenuIds(User user) {
        Set<Long> allowedIds = new HashSet<>();
        for (Role role : user.getRoles()) {
            for (Menu menu : role.getMenus()) {
                if (isAccessibleMenu(menu)) {
                    allowedIds.add(menu.getId());
                }
            }
            for (Menu menu : roleMenuResolver.resolveMenus(role.getPermissions())) {
                if (isAccessibleMenu(menu)) {
                    allowedIds.add(menu.getId());
                }
            }
        }
        return allowedIds;
    }

    private Set<Long> expandMenuClosure(
            Set<Long> seedIds,
            Map<Long, Menu> byId,
            Map<Long, List<Menu>> childrenByParent) {
        Set<Long> expandedIds = new HashSet<>();
        for (Long menuId : seedIds) {
            Menu menu = byId.get(menuId);
            if (menu == null) {
                continue;
            }
            expandedIds.add(menuId);
            addAncestorIds(menu, byId, expandedIds);
            addDescendantIds(menu, childrenByParent, expandedIds);
        }
        return expandedIds;
    }

    private boolean isAccessibleMenu(Menu menu) {
        return Boolean.TRUE.equals(menu.getIsVisible()) && Boolean.TRUE.equals(menu.getIsEnabled());
    }

    private boolean shouldAssignMenuToUserRole(Menu menu) {
        if (menu.getPath() == null) {
            return false;
        }
        if (menu.getPath().startsWith("/documents/") && !menu.getPath().contains(":")) {
            return true;
        }
        return "/dashboard".equals(menu.getPath())
                || "/tasks".equals(menu.getPath())
                || "/documents".equals(menu.getPath());
    }

    private void addDescendantIds(Menu menu, Map<Long, List<Menu>> childrenByParent, Set<Long> allowedIds) {
        List<Menu> children = childrenByParent.get(menu.getId());
        if (children == null || children.isEmpty()) {
            return;
        }
        for (Menu child : children) {
            if (!isAccessibleMenu(child)) {
                continue;
            }
            if (allowedIds.add(child.getId())) {
                addDescendantIds(child, childrenByParent, allowedIds);
            }
        }
    }

    private void addAncestorIds(Menu menu, Map<Long, Menu> byId, Set<Long> allowedIds) {
        Long parentId = menu.getParentId();
        while (parentId != null) {
            Menu parent = byId.get(parentId);
            if (parent == null) {
                break;
            }
            if (!isAccessibleMenu(parent)) {
                break;
            }
            allowedIds.add(parent.getId());
            parentId = parent.getParentId();
        }
    }

    private List<MenuDTO> buildTreeFromMenuList(List<Menu> menus) {
        Set<Long> idSet = menus.stream().map(Menu::getId).collect(Collectors.toSet());
        Map<Long, List<Menu>> childrenMap = menus.stream()
                .filter(m -> m.getParentId() != null && idSet.contains(m.getParentId()))
                .collect(Collectors.groupingBy(Menu::getParentId));

        return menus.stream()
                .filter(m -> m.getParentId() == null || !idSet.contains(m.getParentId()))
                .sorted(menuSortComparator())
                .map(m -> toDTOWithChildren(m, childrenMap))
                .collect(Collectors.toList());
    }

    private Comparator<Menu> menuSortComparator() {
        return (a, b) -> Integer.compare(
                a.getSortOrder() != null ? a.getSortOrder() : 0,
                b.getSortOrder() != null ? b.getSortOrder() : 0);
    }

    private MenuDTO toDTO(Menu menu) {
        MenuDTO dto = new MenuDTO();
        dto.setId(menu.getId());
        dto.setName(menu.getName());
        dto.setParentId(menu.getParentId());
        dto.setIcon(menu.getIcon());
        dto.setPath(menu.getPath());
        dto.setComponentPath(menu.getComponentPath());
        dto.setSortOrder(menu.getSortOrder());
        dto.setIsVisible(menu.getIsVisible());
        dto.setIsEnabled(menu.getIsEnabled());
        return dto;
    }

    private MenuDTO toDTOWithChildren(Menu menu, Map<Long, List<Menu>> childrenMap) {
        MenuDTO dto = toDTO(menu);

        List<Menu> children = childrenMap.getOrDefault(menu.getId(), new ArrayList<>());
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