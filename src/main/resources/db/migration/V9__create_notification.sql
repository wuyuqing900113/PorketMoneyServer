-- ============================================================
-- V9：通知（站内信）+ 外部通道投递记录（M5-detailed-design.md §9.1）
-- 设计基线：站内信即 notification 行本身（read_at 表达已读）；
-- 外部通道投递与重试独立 notification_delivery（站内信不产生 delivery 行）。
-- 时间类型统一写作 TIMESTAMP WITH TIME ZONE（H2 2.4.240 不识别 TIMESTAMPTZ，约定同 V2/V4/V7）。
-- ============================================================

CREATE TABLE notification (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES app_user (id),   -- 接收人
    family_id    BIGINT NOT NULL REFERENCES family (id),
    type         VARCHAR(32) NOT NULL
                 CHECK (type IN ('TX_IN', 'TX_OUT', 'LOW_BALANCE', 'RULE_EXPIRED')),
    title        VARCHAR(128) NOT NULL,
    content      VARCHAR(512) NOT NULL,
    biz_ref_type VARCHAR(24),                                -- MONEY_TRANSACTION / MONEY_RULE
    biz_ref_id   BIGINT,
    read_at      TIMESTAMP WITH TIME ZONE,                   -- NULL = 未读
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_notify_user_time ON notification (user_id, created_at);
CREATE INDEX idx_notify_user_read ON notification (user_id, read_at);

COMMENT ON TABLE notification IS '站内信通知：接收人维度，read_at 表达已读';
COMMENT ON COLUMN notification.biz_ref_type IS '业务锚点类型（流水/规则），可追溯通知来源';

CREATE TABLE notification_delivery (
    id              BIGSERIAL PRIMARY KEY,
    notification_id BIGINT NOT NULL REFERENCES notification (id),
    channel         VARCHAR(16) NOT NULL DEFAULT 'PUSH'
                    CHECK (channel IN ('PUSH')),
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'DEAD')),
    retry_count     INTEGER NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_error      VARCHAR(256),
    sent_at         TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_ndelivery_pending ON notification_delivery (status, next_retry_at);
CREATE INDEX idx_ndelivery_notification ON notification_delivery (notification_id);

COMMENT ON TABLE notification_delivery IS '外部通道投递与重试记录：PENDING→SENT/DEAD，站内信无此记录';
