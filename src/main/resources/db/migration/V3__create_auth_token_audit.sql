-- ============================================================
-- V3：refresh 令牌 / 审计日志 / OAuth2 扩展点预留（M1 设计 §7.2）
-- 时间类型统一写作 TIMESTAMP WITH TIME ZONE（同 V2 说明）。
-- ============================================================

CREATE TABLE user_refresh_token (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES app_user (id),
    token_hash CHAR(64) NOT NULL,               -- SHA-256(refreshToken)，不落明文
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,        -- 软吊销（§4.3 吊销时机表）
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX idx_refresh_token_user ON user_refresh_token (user_id);

CREATE TABLE audit_log (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT,                         -- 匿名事件（如注册前）可为空
    action      VARCHAR(48) NOT NULL,
    target_type VARCHAR(32),
    target_id   VARCHAR(64),
    detail      JSONB,                          -- 结构化补充信息（脱敏后）
    client_ip   VARCHAR(45),                    -- 兼容 IPv6
    trace_id    VARCHAR(64),                    -- 关联 MDC traceId
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_user_time   ON audit_log (user_id, created_at);
CREATE INDEX idx_audit_action_time ON audit_log (action, created_at);

-- OAuth2 扩展点预留（§4.7）：M1 建表不实现代码
CREATE TABLE user_oauth_binding (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES app_user (id),
    provider    VARCHAR(32)  NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_oauth_provider_external UNIQUE (provider, external_id)
);
