-- User profile extensions: real-name verification
-- Run once against translation_app database before starting backend (JPA ddl-auto=validate)

USE translation_app;

ALTER TABLE users
    ADD COLUMN id_card_number VARCHAR(32) NULL AFTER city;

ALTER TABLE users
    ADD COLUMN real_name_verified BIT(1) DEFAULT 0 AFTER id_card_number;
