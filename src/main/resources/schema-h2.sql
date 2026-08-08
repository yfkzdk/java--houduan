-- H2 兼容的 schema（从 MySQL schema.sql 转换）
-- 自动执行，无需手动建库

CREATE TABLE IF NOT EXISTS users (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(256) NOT NULL,
    email       VARCHAR(128),
    nickname    VARCHAR(200),
    avatar_url  VARCHAR(512),
    status      TINYINT DEFAULT 1,
    quota_total INT     DEFAULT 100,
    quota_used  INT     DEFAULT 0,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 预置测试账号: test / 123456
INSERT INTO users (username, password, nickname, email) VALUES
('test', '$2a$10$NZ5o7r2E.ayT2ZoxgjlI.eJ6OEYqjH7INR/F.mXDbjZJi9HF0YCVG', '测试用户', 'test@example.com');

CREATE TABLE IF NOT EXISTS conversations (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    thread_id   VARCHAR(64) NOT NULL UNIQUE,
    title       VARCHAR(128) DEFAULT '新对话',
    status      VARCHAR(16) DEFAULT 'ACTIVE',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_call_logs (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id          BIGINT,
    thread_id        VARCHAR(64),
    model            VARCHAR(32) DEFAULT 'deepseek-chat',
    tokens_used      INT DEFAULT 0,
    latency_ms       INT DEFAULT 0,
    success          TINYINT DEFAULT 1,
    error_message    VARCHAR(512),
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
