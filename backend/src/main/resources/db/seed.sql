-- TranslationApp seed data
-- Default passwords: admin/admin123, user/user123 (BCrypt hashes below)
-- Note: DataInitializer also idempotently fills missing menus/permissions and syncs role links on startup.
USE translation_app;

-- Permissions (core modules) — full set created by DataInitializer when empty
INSERT INTO permissions (id, code, name, description, module_name, parent_id, sort_order, created_at, updated_at) VALUES
(1, 'system', '系统管理', '系统管理模块', 'system', NULL, 0, NOW(), NOW()),
(2, 'system:user', '用户管理', '用户管理权限', 'system', 1, 1, NOW(), NOW()),
(3, 'document', '文档管理', '文档管理模块', 'document', NULL, 1, NOW(), NOW()),
(4, 'document:view', '查看文档', NULL, 'document', 3, 1, NOW(), NOW()),
(5, 'document:edit', '编辑文档', NULL, 'document', 3, 2, NOW(), NOW()),
(6, 'task', '任务管理', '任务管理模块', 'task', NULL, 2, NOW(), NOW()),
(7, 'task:view', '查看任务', NULL, 'task', 6, 1, NOW(), NOW()),
(8, 'task:create', '创建任务', NULL, 'task', 6, 2, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Menus (minimal bootstrap; DataInitializer ensures the full tree by path)
INSERT INTO menus (id, name, parent_id, icon, path, component_path, sort_order, is_visible, is_enabled, created_at, updated_at) VALUES
(1, '首页', NULL, 'HomeFilled', '/dashboard', NULL, 1, 1, 1, NOW(), NOW()),
(2, '爬取任务', NULL, 'List', '/tasks', NULL, 2, 1, 1, NOW(), NOW()),
(3, '文档列表', NULL, 'Document', '/documents', NULL, 3, 1, 1, NOW(), NOW()),
(4, '系统管理', NULL, 'Setting', NULL, NULL, 5, 1, 1, NOW(), NOW()),
(5, '用户管理', 4, 'User', '/system/users', NULL, 1, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Roles
INSERT INTO roles (id, name, description, is_system, created_at, updated_at) VALUES
(1, 'ADMIN', '系统管理员，拥有所有权限', 1, NOW(), NOW()),
(2, 'USER', '普通用户，拥有基本权限', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description);

-- Role permissions
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT 1, id FROM permissions;

INSERT IGNORE INTO role_permissions (role_id, permission_id) VALUES
(2, 4), (2, 7), (2, 8);

-- Role menus (ADMIN gets all current menus; startup sync adds any missing links)
INSERT IGNORE INTO role_menus (role_id, menu_id)
SELECT 1, id FROM menus;

INSERT IGNORE INTO role_menus (role_id, menu_id) VALUES
(2, 1), (2, 2), (2, 3);

-- Users (passwords: admin123 / user123)
INSERT INTO users (id, username, password, real_name, is_enabled, is_locked, created_at, updated_at) VALUES
(1, 'admin', '$2a$10$RnXq6rAGSwg.bgSLTOAOCepw7edm4hM9HnKi103q5D9wK75IghHum', '管理员', 1, 0, NOW(), NOW()),
(2, 'user', '$2a$10$Xj08yogmASb9By2CEiyYq.NxPN/0OZ1/mP46B3ZB.oSQDvNxshcVO', '普通用户', 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE real_name = VALUES(real_name);

INSERT IGNORE INTO user_roles (user_id, role_id) VALUES (1, 1), (2, 2);
