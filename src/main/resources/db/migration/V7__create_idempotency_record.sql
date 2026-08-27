-- M3：幂等记录表（M3-detailed-design.md §5）
-- 两阶段幂等：先插 IN_PROGRESS（uk_idem_user_key 唯一约束兜底并发），
-- 业务成功后回填 resp + PROCESSED，失败删除释放键供修正后重试。
-- payload_hash = SHA-256(method || '\n' || path || '\n' || body)，
-- 用于同键不同请求体检测（100009）；resp_body 缓存原始成功响应 JSON。
-- 说明：时间类型统一写作 TIMESTAMP WITH TIME ZONE（H2 2.4.240 不识别
-- TIMESTAMPTZ 缩写，约定同 V2/V4）；resp_body 为 VARCHAR（原始 JSON 文本，
-- 避免 JSONB 在 H2 上回读时被包一层引号，H2 与 PostgreSQL 的 JSON 序列化差异）。

CREATE TABLE idempotency_record (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES app_user (id),
    idem_key     VARCHAR(64) NOT NULL,
    method       VARCHAR(8) NOT NULL,
    path         VARCHAR(128) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    resp_code    INTEGER,
    resp_body    VARCHAR(8192),
    status       VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS'
                 CHECK (status IN ('IN_PROGRESS', 'PROCESSED')),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_idem_user_key UNIQUE (user_id, idem_key)
);

CREATE INDEX idx_idem_expires ON idempotency_record (expires_at);

COMMENT ON TABLE idempotency_record IS '幂等记录：写操作请求指纹 + 原始响应缓存（TTL 默认 7 天）';
COMMENT ON COLUMN idempotency_record.payload_hash IS '请求指纹 SHA-256(method||path||body)，同键不同体检测';
COMMENT ON COLUMN idempotency_record.resp_body IS '成功响应的原始 JSON 缓存，重放返回原响应';
