-- Analytics & user profile fields for big-screen dashboard
USE translation_app;

ALTER TABLE users
    ADD COLUMN gender VARCHAR(16) NULL DEFAULT 'UNKNOWN' AFTER avatar,
    ADD COLUMN birth_date DATE NULL AFTER gender,
    ADD COLUMN province VARCHAR(64) NULL AFTER birth_date,
    ADD COLUMN city VARCHAR(64) NULL AFTER province;

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
