-- Phase 1: 核心表
-- 数据库: agentic_chatbot

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    username    VARCHAR(64)  NOT NULL UNIQUE,
    password    VARCHAR(256) NOT NULL COMMENT 'BCrypt',
    email       VARCHAR(128),
    nickname    VARCHAR(200),
    avatar_url  VARCHAR(512),
    status      TINYINT DEFAULT 1 COMMENT '1:启用 0:禁用',
    quota_total INT     DEFAULT 100 COMMENT '每月对话额度',
    quota_used  INT     DEFAULT 0,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 预置测试账号: test / 123456
INSERT INTO users (username, password, nickname, email) VALUES
('test', '$2a$10$NZ5o7r2E.ayT2ZoxgjlI.eJ6OEYqjH7INR/F.mXDbjZJi9HF0YCVG', '测试用户', 'test@example.com');

-- 对话会话表 (对应 Python 侧 LangGraph thread_id)
CREATE TABLE IF NOT EXISTS conversations (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id     BIGINT NOT NULL,
    thread_id   VARCHAR(64) NOT NULL UNIQUE COMMENT 'LangGraph thread_id',
    title       VARCHAR(128) DEFAULT '新对话',
    status      VARCHAR(16) DEFAULT 'ACTIVE' COMMENT 'ACTIVE / ARCHIVED',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user (user_id),
    INDEX idx_thread (thread_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话会话表';

-- AI 调用审计日志
CREATE TABLE IF NOT EXISTS ai_call_logs (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id          BIGINT,
    thread_id        VARCHAR(64),
    model            VARCHAR(32) DEFAULT 'deepseek-chat',
    tokens_used      INT        DEFAULT 0,
    latency_ms       INT        DEFAULT 0,
    success          TINYINT    DEFAULT 1,
    error_message    VARCHAR(512),
    created_at       DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_date (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用审计日志';
