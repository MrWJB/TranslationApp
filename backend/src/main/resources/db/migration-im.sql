-- IM module schema (run manually after schema.sql)
USE translation_app;

CREATE TABLE IF NOT EXISTS departments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    parent_id BIGINT NULL,
    sort_order INT DEFAULT 0,
    leader_user_id BIGINT NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_departments_parent (parent_id),
    CONSTRAINT fk_departments_parent FOREIGN KEY (parent_id) REFERENCES departments (id),
    CONSTRAINT fk_departments_leader FOREIGN KEY (leader_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_departments (
    user_id BIGINT NOT NULL,
    department_id BIGINT NOT NULL,
    is_primary BIT(1) DEFAULT 0,
    PRIMARY KEY (user_id, department_id),
    CONSTRAINT fk_user_departments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_departments_dept FOREIGN KEY (department_id) REFERENCES departments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friendships (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    friend_user_id BIGINT NOT NULL,
    remark VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_friendships_pair (user_id, friend_user_id),
    CONSTRAINT fk_friendships_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_friendships_friend FOREIGN KEY (friend_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friend_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    message VARCHAR(500) NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_friend_requests_to (to_user_id, status),
    CONSTRAINT fk_friend_requests_from FOREIGN KEY (from_user_id) REFERENCES users (id),
    CONSTRAINT fk_friend_requests_to FOREIGN KEY (to_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    type VARCHAR(32) NOT NULL,
    title VARCHAR(255) NULL,
    avatar_url VARCHAR(512) NULL,
    announcement TEXT NULL,
    owner_id BIGINT NULL,
    last_message_id BIGINT NULL,
    last_message_at DATETIME(6) NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_conversations_last_message (last_message_at),
    CONSTRAINT fk_conversations_owner FOREIGN KEY (owner_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation_members (
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    mute_until DATETIME(6) NULL,
    pinned BIT(1) DEFAULT 0,
    joined_at DATETIME(6) NULL,
    last_read_message_id BIGINT NULL,
    unread_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_conv_members_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_conv_members_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS attachments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    uploader_id BIGINT NOT NULL,
    bucket VARCHAR(255) NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    file_name VARCHAR(512) NOT NULL,
    mime_type VARCHAR(255) NULL,
    size_bytes BIGINT NULL,
    sha256 VARCHAR(64) NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_attachments_uploader FOREIGN KEY (uploader_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    content TEXT NULL,
    attachment_id BIGINT NULL,
    reply_to_id BIGINT NULL,
    client_msg_id VARCHAR(64) NULL,
    created_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_messages_client_msg (conversation_id, client_msg_id),
    KEY idx_messages_conv_time (conversation_id, created_at),
    FULLTEXT KEY ft_messages_content (content),
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id),
    CONSTRAINT fk_messages_attachment FOREIGN KEY (attachment_id) REFERENCES attachments (id),
    CONSTRAINT fk_messages_reply FOREIGN KEY (reply_to_id) REFERENCES messages (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS message_reads (
    message_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    read_at DATETIME(6) NULL,
    PRIMARY KEY (message_id, user_id),
    CONSTRAINT fk_message_reads_message FOREIGN KEY (message_id) REFERENCES messages (id),
    CONSTRAINT fk_message_reads_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS call_sessions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT NOT NULL,
    initiator_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RINGING',
    started_at DATETIME(6) NULL,
    ended_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_call_sessions_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_call_sessions_initiator FOREIGN KEY (initiator_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS call_participants (
    call_session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME(6) NULL,
    left_at DATETIME(6) NULL,
    PRIMARY KEY (call_session_id, user_id),
    CONSTRAINT fk_call_participants_session FOREIGN KEY (call_session_id) REFERENCES call_sessions (id),
    CONSTRAINT fk_call_participants_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
