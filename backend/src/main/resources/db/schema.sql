-- TranslationApp MySQL schema
-- Database: translation_app (create manually if not using docker-compose)

CREATE DATABASE IF NOT EXISTS translation_app
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE translation_app;

CREATE TABLE IF NOT EXISTS permissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(255) NOT NULL,
    name VARCHAR(255) NULL,
    description VARCHAR(255) NULL,
    module_name VARCHAR(255) NULL,
    parent_id BIGINT NULL,
    sort_order INT DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_permissions_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS menus (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    parent_id BIGINT NULL,
    icon VARCHAR(255) NULL,
    path VARCHAR(255) NULL,
    component_path VARCHAR(255) NULL,
    sort_order INT DEFAULT 0,
    is_visible BIT(1) DEFAULT 1,
    is_enabled BIT(1) DEFAULT 1,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS roles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    is_system BIT(1) DEFAULT 0,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255) NULL,
    phone VARCHAR(255) NULL,
    phone_verified BIT(1) DEFAULT 0,
    real_name VARCHAR(255) NULL,
    avatar VARCHAR(255) NULL,
    gender VARCHAR(16) NULL DEFAULT 'UNKNOWN',
    birth_date DATE NULL,
    province VARCHAR(64) NULL,
    city VARCHAR(64) NULL,
    id_card_number VARCHAR(32) NULL,
    real_name_verified BIT(1) DEFAULT 0,
    is_enabled BIT(1) DEFAULT 1,
    is_locked BIT(1) DEFAULT 0,
    last_login_time DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS role_menus (
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, menu_id),
    CONSTRAINT fk_role_menus_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_menus_menu FOREIGN KEY (menu_id) REFERENCES menus (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_oauth_bindings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    open_id VARCHAR(255) NOT NULL,
    union_id VARCHAR(255) NULL,
    nickname VARCHAR(255) NULL,
    avatar_url VARCHAR(512) NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_oauth_provider_open_id (provider, open_id),
    KEY idx_oauth_user_id (user_id),
    CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS crawl_tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    url VARCHAR(2048) NOT NULL,
    title VARCHAR(512) NOT NULL,
    status VARCHAR(32) NOT NULL,
    task_type VARCHAR(32) NOT NULL DEFAULT 'document',
    category VARCHAR(64) NULL,
    media_category VARCHAR(64) NULL,
    video_quality INT NULL,
    extract_audio BIT(1) DEFAULT 0,
    extract_subtitles BIT(1) DEFAULT 0,
    type_confidence DOUBLE NULL,
    type_reason VARCHAR(512) NULL,
    user_confirmed_type BIT(1) DEFAULT 0,
    original_content TEXT NULL,
    translated_content TEXT NULL,
    table_of_contents TEXT NULL,
    error_message LONGTEXT NULL,
    max_pages INT NULL,
    progress_phase VARCHAR(32) NULL,
    progress_current INT NULL DEFAULT 0,
    progress_total INT NULL DEFAULT 0,
    progress_message VARCHAR(512) NULL,
    quality_report TEXT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(512) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    task_id BIGINT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    section_level INT DEFAULT 0,
    section_id VARCHAR(255) NULL,
    parent_document_id BIGINT NULL,
    original_content TEXT NULL,
    translated_content TEXT NULL,
    table_of_contents TEXT NULL,
    local_path VARCHAR(1024) NULL,
    category VARCHAR(64) NULL,
    translated_local_path VARCHAR(1024) NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_documents_task FOREIGN KEY (task_id) REFERENCES crawl_tasks (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_documents_task_id ON documents (task_id);
CREATE INDEX idx_documents_local_path ON documents (local_path(255));
CREATE INDEX idx_crawl_tasks_status ON crawl_tasks (status);

CREATE TABLE IF NOT EXISTS user_login_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    login_time DATETIME(6) NOT NULL,
    ip_address VARCHAR(64) NULL,
    province VARCHAR(64) NULL,
    city VARCHAR(64) NULL,
    PRIMARY KEY (id),
    KEY idx_login_log_time (login_time),
    KEY idx_login_log_user (user_id),
    CONSTRAINT fk_login_log_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS analytics_online_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    snapshot_time DATETIME(6) NOT NULL,
    online_count INT NOT NULL DEFAULT 0,
    total_users INT NOT NULL DEFAULT 0,
    male_online INT NOT NULL DEFAULT 0,
    female_online INT NOT NULL DEFAULT 0,
    other_online INT NOT NULL DEFAULT 0,
    unknown_online INT NOT NULL DEFAULT 0,
    age_under_18 INT NOT NULL DEFAULT 0,
    age_18_24 INT NOT NULL DEFAULT 0,
    age_25_34 INT NOT NULL DEFAULT 0,
    age_35_44 INT NOT NULL DEFAULT 0,
    age_45_54 INT NOT NULL DEFAULT 0,
    age_55_plus INT NOT NULL DEFAULT 0,
    region_stats_json TEXT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_snapshot_time (snapshot_time),
    KEY idx_snapshot_time (snapshot_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
