-- ============================================================
-- V8：AI 交互会话域（M4 设计 §10.1）
-- 会话 / 消息 / 待确认动作三表；时间类型统一写作
-- TIMESTAMP WITH TIME ZONE（H2 2.4.240 不识别 TIMESTAMPTZ 缩写，
-- 约定同 V2）。tool_call_json / params_json 用 JSONB 落调用链与参数快照。
-- 脚本一经提交永不修改；回滚走新脚本前向修复（M0 既定规范）。
-- ============================================================

CREATE TABLE ai_session (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT NOT NULL REFERENCES app_user (id),
    family_id      BIGINT NOT NULL REFERENCES family (id),
    channel        VARCHAR(8) NOT NULL DEFAULT 'TEXT',   -- TEXT / VOICE
    status         VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                   CHECK (status IN ('ACTIVE', 'CLOSED')),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    last_active_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_session_user ON ai_session (user_id, last_active_at);

COMMENT ON TABLE ai_session IS 'AI 交互会话：一人一活跃会话，消息按会话归属';

CREATE TABLE ai_message (
    id            BIGSERIAL PRIMARY KEY,
    session_id    BIGINT NOT NULL REFERENCES ai_session (id),
    role          VARCHAR(16) NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    content       TEXT,                                 -- 用户原文 / AI 回复文本
    intent        VARCHAR(32),                          -- 意图码（USER 消息为 NULL）
    tool_call_json JSONB,                               -- 意图→参数→工具调用→结果 调用链
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_message_session ON ai_message (session_id, created_at);

COMMENT ON TABLE ai_message IS 'AI 消息：tool_call_json 落调用链供可解释性追溯（§7.4）';

CREATE TABLE ai_pending_action (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT NOT NULL REFERENCES ai_session (id),
    user_id     BIGINT NOT NULL REFERENCES app_user (id),
    intent      VARCHAR(32) NOT NULL CHECK (intent IN ('DEPOSIT', 'WITHDRAW')),
    params_json JSONB NOT NULL,                         -- {targetUserId, amount, remark} 快照
    status      VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING', 'EXECUTED', 'REJECTED', 'CANCELED', 'EXPIRED')),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    executed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_ai_pending_status  ON ai_pending_action (status, expires_at);
CREATE INDEX idx_ai_pending_session ON ai_pending_action (session_id);

COMMENT ON TABLE ai_pending_action IS '资金写二次确认动作：参数快照 + TTL 状态机（PENDING→终态）';
