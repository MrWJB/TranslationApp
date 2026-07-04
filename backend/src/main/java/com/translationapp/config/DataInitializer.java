package com.translationapp.config;

import com.translationapp.entity.*;
import com.translationapp.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final MenuRepository menuRepository;
    private final PasswordEncoder passwordEncoder;

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

        // Initialize permissions
        initPermissions();

        // Initialize menus
        initMenus();

        // Initialize roles
        initRoles();

        // Initialize users
        initUsers();
    }

    private void initPermissions() {
        if (permissionRepository.count() > 0) return;

        // System management permissions
        Permission systemModule = createPermission("system", "系统管理", "系统管理模块", null, 0);

        Permission userManage = createPermission("system:user", "用户管理", "用户管理权限", systemModule.getId(), 1);
        createPermission("system:user:create", "创建用户", null, userManage.getId(), 1);
        createPermission("system:user:update", "编辑用户", null, userManage.getId(), 2);
        createPermission("system:user:delete", "删除用户", null, userManage.getId(), 3);
        createPermission("system:user:resetpwd", "重置密码", null, userManage.getId(), 4);

        Permission roleManage = createPermission("system:role", "角色管理", "角色管理权限", systemModule.getId(), 2);
        createPermission("system:role:create", "创建角色", null, roleManage.getId(), 1);
        createPermission("system:role:update", "编辑角色", null, roleManage.getId(), 2);
        createPermission("system:role:delete", "删除角色", null, roleManage.getId(), 3);

        Permission permManage = createPermission("system:permission", "权限管理", "权限管理权限", systemModule.getId(), 3);
        createPermission("system:permission:create", "创建权限", null, permManage.getId(), 1);
        createPermission("system:permission:update", "编辑权限", null, permManage.getId(), 2);
        createPermission("system:permission:delete", "删除权限", null, permManage.getId(), 3);

        Permission menuManage = createPermission("system:menu", "菜单管理", "菜单管理权限", systemModule.getId(), 4);
        createPermission("system:menu:create", "创建菜单", null, menuManage.getId(), 1);
        createPermission("system:menu:update", "编辑菜单", null, menuManage.getId(), 2);
        createPermission("system:menu:delete", "删除菜单", null, menuManage.getId(), 3);

        // Document management permissions
        Permission docModule = createPermission("document", "文档管理", "文档管理模块", null, 1);
        createPermission("document:view", "查看文档", null, docModule.getId(), 1);
        createPermission("document:edit", "编辑文档", null, docModule.getId(), 2);
        createPermission("document:delete", "删除文档", null, docModule.getId(), 3);

        // Task management permissions
        Permission taskModule = createPermission("task", "任务管理", "任务管理模块", null, 2);
        createPermission("task:view", "查看任务", null, taskModule.getId(), 1);
        createPermission("task:create", "创建任务", null, taskModule.getId(), 2);
        createPermission("task:delete", "删除任务", null, taskModule.getId(), 3);

        // Video management permissions
        Permission videoModule = createPermission("video", "视频管理", "视频管理模块", null, 3);
        Permission animeManage = createPermission("video:anime", "动漫管理", "动漫管理权限", videoModule.getId(), 1);
        createPermission("video:anime:view", "查看动漫", null, animeManage.getId(), 1);
        createPermission("video:anime:create", "新增动漫", null, animeManage.getId(), 2);
        createPermission("video:anime:update", "编辑动漫", null, animeManage.getId(), 3);
        createPermission("video:anime:delete", "删除动漫", null, animeManage.getId(), 4);

        Permission shortDramaManage = createPermission("video:short-drama", "短剧管理", "短剧管理权限", videoModule.getId(), 2);
        createPermission("video:short-drama:view", "查看短剧", null, shortDramaManage.getId(), 1);
        createPermission("video:short-drama:create", "新增短剧", null, shortDramaManage.getId(), 2);
        createPermission("video:short-drama:update", "编辑短剧", null, shortDramaManage.getId(), 3);
        createPermission("video:short-drama:delete", "删除短剧", null, shortDramaManage.getId(), 4);

        Permission tvSeriesManage = createPermission("video:tv-series", "电视剧管理", "电视剧管理权限", videoModule.getId(), 3);
        createPermission("video:tv-series:view", "查看电视剧", null, tvSeriesManage.getId(), 1);
        createPermission("video:tv-series:create", "新增电视剧", null, tvSeriesManage.getId(), 2);
        createPermission("video:tv-series:update", "编辑电视剧", null, tvSeriesManage.getId(), 3);
        createPermission("video:tv-series:delete", "删除电视剧", null, tvSeriesManage.getId(), 4);

        Permission movieManage = createPermission("video:movie", "电影管理", "电影管理权限", videoModule.getId(), 4);
        createPermission("video:movie:view", "查看电影", null, movieManage.getId(), 1);
        createPermission("video:movie:create", "新增电影", null, movieManage.getId(), 2);
        createPermission("video:movie:update", "编辑电影", null, movieManage.getId(), 3);
        createPermission("video:movie:delete", "删除电影", null, movieManage.getId(), 4);

        Permission varietyManage = createPermission("video:variety", "综艺管理", "综艺管理权限", videoModule.getId(), 5);
        createPermission("video:variety:view", "查看综艺", null, varietyManage.getId(), 1);
        createPermission("video:variety:create", "新增综艺", null, varietyManage.getId(), 2);
        createPermission("video:variety:update", "编辑综艺", null, varietyManage.getId(), 3);
        createPermission("video:variety:delete", "删除综艺", null, varietyManage.getId(), 4);
    }

    private Permission createPermission(String code, String name, String description, Long parentId, int sortOrder) {
        Permission permission = new Permission();
        permission.setCode(code);
        permission.setName(name);
        permission.setDescription(description);
        permission.setParentId(parentId);
        permission.setSortOrder(sortOrder);
        return permissionRepository.save(permission);
    }

    private void initMenus() {
        if (menuRepository.count() > 0) return;

        // Main menus
        Menu dashboardMenu = createMenu("首页", null, "HomeFilled", "/dashboard", null, 1, true, true);

        Menu taskMenu = createMenu("爬取任务", null, "List", "/tasks", null, 2, true, true);
        createMenu("任务详情", taskMenu.getId(), null, "/tasks/:id", null, 1, true, true);

        Menu docMenu = createMenu("文档列表", null, "Document", "/documents", null, 3, true, true);
        createMenu("Java", docMenu.getId(), "Notebook", "/documents/java", null, 1, true, true);
        createMenu("Spring", docMenu.getId(), "Connection", "/documents/spring", null, 2, true, true);
        createMenu("Spring Boot", docMenu.getId(), "Lightning", "/documents/spring-boot", null, 3, true, true);
        createMenu("Spring Cloud", docMenu.getId(), "Cloudy", "/documents/spring-cloud", null, 4, true, true);
        createMenu("Spring Mvc", docMenu.getId(), "DataLine", "/documents/spring-mvc", null, 5, true, true);
        createMenu("Mysql", docMenu.getId(), "DataAnalysis", "/documents/mysql", null, 6, true, true);
        createMenu("Oracle", docMenu.getId(), "Lock", "/documents/oracle", null, 7, true, true);
        createMenu("文档详情", docMenu.getId(), null, "/documents/:id", null, 99, false, true);

        Menu videoMenu = createMenu("视频管理", null, "VideoPlay", null, null, 4, true, true);
        createMenu("动漫", videoMenu.getId(), "MagicStick", "/video/anime", null, 1, true, true);
        createMenu("短剧", videoMenu.getId(), "Film", "/video/short-drama", null, 2, true, true);
        createMenu("电视剧", videoMenu.getId(), "Monitor", "/video/tv-series", null, 3, true, true);
        createMenu("电影", videoMenu.getId(), "VideoCamera", "/video/movie", null, 4, true, true);
        createMenu("综艺", videoMenu.getId(), "Microphone", "/video/variety", null, 5, true, true);

        Menu systemMenu = createMenu("系统管理", null, "Setting", null, null, 5, true, true);
        createMenu("用户管理", systemMenu.getId(), "User", "/system/users", null, 1, true, true);
        createMenu("角色管理", systemMenu.getId(), "UserFilled", "/system/roles", null, 2, true, true);
        createMenu("权限管理", systemMenu.getId(), "Key", "/system/permissions", null, 3, true, true);
        createMenu("菜单管理", systemMenu.getId(), "Menu", "/system/menus", null, 4, true, true);
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