package com.translationapp.config;

import com.translationapp.entity.*;
import com.translationapp.repository.*;
import com.translationapp.service.MenuService;
import com.translationapp.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Set;

/**
 * 应用启动时的基础数据初始化器。
 */
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final MenuRepository menuRepository;
    private final PasswordEncoder passwordEncoder;
    private final MenuService menuService;
    private final PermissionService permissionService;

    @Value("${app.seed-users.enabled:true}")
    private boolean seedUsersEnabled;

    @Value("${app.seed-users.admin-password:admin123}")
    private String adminPassword;

    @Value("${app.seed-users.user-password:user123}")
    private String userPassword;

    @Override
    @Transactional
    public void run(String... args) {
        if (!seedUsersEnabled) {
            return;
        }

        // Initialize permissions (idempotent — fills missing entries by code)
        initPermissions();
        syncAdminRolePermissions();

        // Initialize menus
        initMenus();

        // Initialize roles
        initRoles();

        syncAdminRoleMenus();
        syncUserRoleDocumentMenus();

        // Initialize users
        initUsers();
    }

    private void initPermissions() {
        initSystemPermissions();
        initDocumentPermissions();
        initTaskPermissions();
        initVideoPermissions();
    }

    private void initSystemPermissions() {
        Permission systemModule = ensurePermission("system", "系统管理", "系统管理模块", "system", null, 0);

        Permission userManage = ensurePermission("system:user", "用户管理", "用户管理权限", "system", systemModule.getId(), 1);
        ensurePermission("system:user:create", "创建用户", null, "system", userManage.getId(), 1);
        ensurePermission("system:user:update", "编辑用户", null, "system", userManage.getId(), 2);
        ensurePermission("system:user:delete", "删除用户", null, "system", userManage.getId(), 3);
        ensurePermission("system:user:resetpwd", "重置密码", null, "system", userManage.getId(), 4);

        Permission roleManage = ensurePermission("system:role", "角色管理", "角色管理权限", "system", systemModule.getId(), 2);
        ensurePermission("system:role:create", "创建角色", null, "system", roleManage.getId(), 1);
        ensurePermission("system:role:update", "编辑角色", null, "system", roleManage.getId(), 2);
        ensurePermission("system:role:delete", "删除角色", null, "system", roleManage.getId(), 3);

        Permission permManage = ensurePermission("system:permission", "权限管理", "权限管理权限", "system", systemModule.getId(), 3);
        ensurePermission("system:permission:create", "创建权限", null, "system", permManage.getId(), 1);
        ensurePermission("system:permission:update", "编辑权限", null, "system", permManage.getId(), 2);
        ensurePermission("system:permission:delete", "删除权限", null, "system", permManage.getId(), 3);

        Permission menuManage = ensurePermission("system:menu", "菜单管理", "菜单管理权限", "system", systemModule.getId(), 4);
        ensurePermission("system:menu:create", "创建菜单", null, "system", menuManage.getId(), 1);
        ensurePermission("system:menu:update", "编辑菜单", null, "system", menuManage.getId(), 2);
        ensurePermission("system:menu:delete", "删除菜单", null, "system", menuManage.getId(), 3);
    }

    private void initDocumentPermissions() {
        Permission docModule = ensurePermission("document", "文档管理", "文档管理模块", "document", null, 1);
        ensurePermission("document:view", "查看文档", null, "document", docModule.getId(), 1);
        ensurePermission("document:edit", "编辑文档", null, "document", docModule.getId(), 2);
        ensurePermission("document:delete", "删除文档", null, "document", docModule.getId(), 3);
    }

    private void initTaskPermissions() {
        Permission taskModule = ensurePermission("task", "任务管理", "任务管理模块", "task", null, 2);
        ensurePermission("task:view", "查看任务", null, "task", taskModule.getId(), 1);
        ensurePermission("task:create", "创建任务", null, "task", taskModule.getId(), 2);
        ensurePermission("task:delete", "删除任务", null, "task", taskModule.getId(), 3);
    }

    private void initVideoPermissions() {
        Permission videoModule = ensurePermission("video", "视频管理", "视频管理模块", "video", null, 3);
        Permission animeManage = ensurePermission("video:anime", "动漫管理", "动漫管理权限", "video", videoModule.getId(), 1);
        ensurePermission("video:anime:view", "查看动漫", null, "video", animeManage.getId(), 1);
        ensurePermission("video:anime:create", "新增动漫", null, "video", animeManage.getId(), 2);
        ensurePermission("video:anime:update", "编辑动漫", null, "video", animeManage.getId(), 3);
        ensurePermission("video:anime:delete", "删除动漫", null, "video", animeManage.getId(), 4);

        Permission shortDramaManage = ensurePermission("video:short-drama", "短剧管理", "短剧管理权限", "video", videoModule.getId(), 2);
        ensurePermission("video:short-drama:view", "查看短剧", null, "video", shortDramaManage.getId(), 1);
        ensurePermission("video:short-drama:create", "新增短剧", null, "video", shortDramaManage.getId(), 2);
        ensurePermission("video:short-drama:update", "编辑短剧", null, "video", shortDramaManage.getId(), 3);
        ensurePermission("video:short-drama:delete", "删除短剧", null, "video", shortDramaManage.getId(), 4);

        Permission tvSeriesManage = ensurePermission("video:tv-series", "电视剧管理", "电视剧管理权限", "video", videoModule.getId(), 3);
        ensurePermission("video:tv-series:view", "查看电视剧", null, "video", tvSeriesManage.getId(), 1);
        ensurePermission("video:tv-series:create", "新增电视剧", null, "video", tvSeriesManage.getId(), 2);
        ensurePermission("video:tv-series:update", "编辑电视剧", null, "video", tvSeriesManage.getId(), 3);
        ensurePermission("video:tv-series:delete", "删除电视剧", null, "video", tvSeriesManage.getId(), 4);

        Permission movieManage = ensurePermission("video:movie", "电影管理", "电影管理权限", "video", videoModule.getId(), 4);
        ensurePermission("video:movie:view", "查看电影", null, "video", movieManage.getId(), 1);
        ensurePermission("video:movie:create", "新增电影", null, "video", movieManage.getId(), 2);
        ensurePermission("video:movie:update", "编辑电影", null, "video", movieManage.getId(), 3);
        ensurePermission("video:movie:delete", "删除电影", null, "video", movieManage.getId(), 4);

        Permission varietyManage = ensurePermission("video:variety", "综艺管理", "综艺管理权限", "video", videoModule.getId(), 5);
        ensurePermission("video:variety:view", "查看综艺", null, "video", varietyManage.getId(), 1);
        ensurePermission("video:variety:create", "新增综艺", null, "video", varietyManage.getId(), 2);
        ensurePermission("video:variety:update", "编辑综艺", null, "video", varietyManage.getId(), 3);
        ensurePermission("video:variety:delete", "删除综艺", null, "video", varietyManage.getId(), 4);
    }

    /** 按 code 幂等创建权限；已存在则直接返回，不覆盖用户自定义名称。 */
    private Permission ensurePermission(String code, String name, String description, String moduleName,
                                        Long parentId, int sortOrder) {
        return permissionRepository.findByCode(code)
                .orElseGet(() -> createPermission(code, name, description, moduleName, parentId, sortOrder));
    }

    private Permission createPermission(String code, String name, String description, String moduleName,
                                        Long parentId, int sortOrder) {
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setName(name);
        permission.setDescription(description);
        permission.setModuleName(moduleName);
        permission.setParentId(parentId);
        permission.setSortOrder(sortOrder);
        return permissionRepository.save(permission);
    }

    private void initMenus() {
        Menu dashboardMenu = ensureMenu("首页", null, "HomeFilled", "/dashboard", null, 1, true, true);

        Menu taskMenu = ensureMenu("爬取任务", null, "List", "/tasks", null, 2, true, true);
        ensureMenu("任务详情", taskMenu.getId(), null, "/tasks/:id", null, 1, false, true);

        Menu docMenu = ensureMenu("文档列表", null, "Document", "/documents", null, 3, true, true);
        ensureMenu("Java", docMenu.getId(), "Notebook", "/documents/java", null, 1, true, true);
        ensureMenu("Spring", docMenu.getId(), "Connection", "/documents/spring", null, 2, true, true);
        ensureMenu("Spring Boot", docMenu.getId(), "Lightning", "/documents/spring-boot", null, 3, true, true);
        ensureMenu("Spring Cloud", docMenu.getId(), "Cloudy", "/documents/spring-cloud", null, 4, true, true);
        ensureMenu("Spring Mvc", docMenu.getId(), "DataLine", "/documents/spring-mvc", null, 5, true, true);
        ensureMenu("Mysql", docMenu.getId(), "DataAnalysis", "/documents/mysql", null, 6, true, true);
        ensureMenu("Oracle", docMenu.getId(), "Lock", "/documents/oracle", null, 7, true, true);
        ensureMenu("文档详情", docMenu.getId(), null, "/documents/:id", null, 99, false, true);

        Menu videoMenu = ensureMenu("视频管理", null, "VideoPlay", null, null, 4, true, true);
        ensureMenu("动漫", videoMenu.getId(), "MagicStick", "/video/anime", null, 1, true, true);
        ensureMenu("短剧", videoMenu.getId(), "Film", "/video/short-drama", null, 2, true, true);
        ensureMenu("电视剧", videoMenu.getId(), "Monitor", "/video/tv-series", null, 3, true, true);
        ensureMenu("电影", videoMenu.getId(), "VideoCamera", "/video/movie", null, 4, true, true);
        ensureMenu("综艺", videoMenu.getId(), "Microphone", "/video/variety", null, 5, true, true);

        Menu systemMenu = ensureMenu("系统管理", null, "Setting", null, null, 5, true, true);
        ensureMenu("数据大屏", null, "DataBoard", "/bigscreen", null, 0, true, true);
        ensureMenu("用户管理", systemMenu.getId(), "User", "/system/users", null, 1, true, true);
        ensureMenu("角色管理", systemMenu.getId(), "UserFilled", "/system/roles", null, 2, true, true);
        ensureMenu("权限管理", systemMenu.getId(), "Key", "/system/permissions", null, 3, true, true);
        ensureMenu("菜单管理", systemMenu.getId(), "Menu", "/system/menus", null, 4, true, true);

        hideDetailRouteMenusFromSidebar();
    }

    /** 详情页路由（含 :param）不应出现在侧边栏，避免父级菜单变成空 sub-menu。 */
    private void hideDetailRouteMenusFromSidebar() {
        for (Menu menu : menuRepository.findAll()) {
            if (menu.getPath() != null && menu.getPath().contains(":") && Boolean.TRUE.equals(menu.getIsVisible())) {
                menu.setIsVisible(false);
                menuRepository.save(menu);
            }
        }
    }

    /**
     * 按 path 幂等创建菜单；无 path 的目录节点按 name + parentId 匹配。
     */
    private Menu ensureMenu(String name, Long parentId, String icon, String path, String componentPath,
                            int sortOrder, boolean visible, boolean enabled) {
        if (path != null && !path.isBlank()) {
            return menuRepository.findByPath(path)
                    .orElseGet(() -> createMenu(name, parentId, icon, path, componentPath, sortOrder, visible, enabled));
        }

        return menuRepository.findAll().stream()
                .filter(menu -> name.equals(menu.getName()))
                .filter(menu -> parentId == null ? menu.getParentId() == null : parentId.equals(menu.getParentId()))
                .findFirst()
                .orElseGet(() -> createMenu(name, parentId, icon, path, componentPath, sortOrder, visible, enabled));
    }

    /**
     * USER 角色若已授权文档列表，则同步其全部可见文档分类子菜单。
     */
    private void syncUserRoleDocumentMenus() {
        roleRepository.findByName("USER").ifPresent(userRole -> {
            menuRepository.findByPath("/documents").ifPresent(docMenu -> {
                boolean hasDocumentRoot = userRole.getMenus().stream()
                        .anyMatch(menu -> docMenu.getId().equals(menu.getId()));
                if (!hasDocumentRoot) {
                    return;
                }

                boolean changed = false;
                for (Menu child : menuRepository.findByParentId(docMenu.getId())) {
                    if (!Boolean.TRUE.equals(child.getIsVisible()) || child.getPath() == null) {
                        continue;
                    }
                    if (child.getPath().contains(":")) {
                        continue;
                    }
                    if (userRole.getMenus().add(child)) {
                        changed = true;
                    }
                }
                if (changed) {
                    roleRepository.save(userRole);
                }
            });
        });
    }

    private Menu createMenu(String name, Long parentId, String icon, String path, String componentPath, int sortOrder, boolean visible, boolean enabled) {
        Menu menu = new Menu();
        menu.setName(name);
        menu.setParentId(parentId);
        menu.setIcon(icon);
        menu.setPath(path);
        menu.setComponentPath(componentPath);
        menu.setSortOrder(sortOrder);
        menu.setIsVisible(visible);
        menu.setIsEnabled(enabled);
        return menuRepository.save(menu);
    }

    private void initRoles() {
        if (roleRepository.count() > 0) return;

        // Admin role - has all permissions and menus
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        adminRole.setDescription("系统管理员，拥有所有权限");
        adminRole.setIsSystem(true);
        adminRole.setPermissions(new HashSet<>(permissionRepository.findAll()));
        adminRole.setMenus(new HashSet<>(menuRepository.findAll()));
        roleRepository.save(adminRole);

        // User role - has basic permissions
        Role userRole = new Role();
        userRole.setName("USER");
        userRole.setDescription("普通用户，拥有基本权限");
        userRole.setIsSystem(true);

        Set<Permission> userPermissions = new HashSet<>();
        permissionRepository.findByCode("document:view").ifPresent(userPermissions::add);
        permissionRepository.findByCode("task:view").ifPresent(userPermissions::add);
        permissionRepository.findByCode("task:create").ifPresent(userPermissions::add);
        userRole.setPermissions(userPermissions);

        Set<Menu> userMenus = new HashSet<>();
        menuRepository.findByPath("/dashboard").ifPresent(userMenus::add);
        menuRepository.findByPath("/tasks").ifPresent(userMenus::add);
        menuRepository.findByPath("/documents").ifPresent(userMenus::add);
        userRole.setMenus(userMenus);

        roleRepository.save(userRole);
    }

    /** 确保 ADMIN 角色始终拥有全部菜单（含后续在菜单管理中新增的项）。 */
    private void syncAdminRoleMenus() {
        menuService.syncAdminRoleMenus();
    }

    /** 确保 ADMIN 角色始终拥有全部权限（含启动时补全的项）。 */
    private void syncAdminRolePermissions() {
        permissionService.syncAdminRolePermissions();
    }

    private void initUsers() {
        Role adminRole = roleRepository.findByName("ADMIN").orElse(null);
        Role userRole = roleRepository.findByName("USER").orElse(null);

        // Create default admin user if not exists
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(adminPassword));
            admin.setRealName("管理员");
            admin.setIsEnabled(true);
            admin.setIsLocked(false);
            if (adminRole != null) {
                admin.setRoles(Set.of(adminRole));
            }
            userRepository.save(admin);
        }

        // Create default user if not exists
        if (!userRepository.existsByUsername("user")) {
            User user = new User();
            user.setUsername("user");
            user.setPassword(passwordEncoder.encode(userPassword));
            user.setRealName("普通用户");
            user.setIsEnabled(true);
            user.setIsLocked(false);
            if (userRole != null) {
                user.setRoles(Set.of(userRole));
            }
            userRepository.save(user);
        }
    }
}