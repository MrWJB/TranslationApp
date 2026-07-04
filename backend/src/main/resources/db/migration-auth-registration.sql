-- Auth registration: phone verification + OAuth bindings
-- Run against translation_app after schema.sql

USE translation_app;

ALTER TABLE users
  ADD COLUMN phone_verified BIT(1) DEFAULT 0 AFTER phone;

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
