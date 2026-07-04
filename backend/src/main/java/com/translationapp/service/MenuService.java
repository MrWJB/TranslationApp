package com.translationapp.service;

import com.translationapp.dto.*;
import com.translationapp.entity.Menu;
import com.translationapp.entity.Role;
import com.translationapp.repository.MenuRepository;
import com.translationapp.repository.RoleRepository;
import com.translationapp.util.SiteKeyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private final MenuRepository menuRepository;
    private final RoleRepository roleRepository;

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

        return toDTO(menuRepository.save(menu));
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

    private void assignMenuToDefaultRoles(Menu menu) {
        roleRepository.findByName("ADMIN").ifPresent(role -> {
            role.getMenus().add(menu);
            roleRepository.save(role);
        });
        roleRepository.findByName("USER").ifPresent(role -> {
            role.getMenus().add(menu);
            roleRepository.save(role);
        });
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