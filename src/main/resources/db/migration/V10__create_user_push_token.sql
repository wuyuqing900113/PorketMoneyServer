-- ============================================================
-- V10：用户外部推送设备令牌（GA D68，鸿蒙 Push Kit）
-- 鸿蒙客户端登录后经 HMS Core 获取 push token 并上报服务端；
-- relay 投递外部推送时按 user_id + provider 取启用中的 token 下发。
-- 一人一渠道一条（UNIQUE(user_id, provider)），重复注册覆盖更新。
-- 时间类型统一写作 TIMESTAMP WITH TIME ZONE（约定同 V2/V4/V7/V9）。
-- ============================================================

CREATE TABLE user_push_token (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES app_user (id),
    provider   VARCHAR(16) NOT NULL DEFAULT 'HARMONY'
               CHECK (provider IN ('HARMONY')),
    token      VARCHAR(256) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (user_id, provider)
);

CREATE INDEX idx_user_push_token_user ON user_push_token (user_id, provider);

COMMENT ON TABLE user_push_token IS '外部推送设备令牌：客户端上报，relay 下发时按用户+渠道取启用令牌';
COMMENT ON COLUMN user_push_token.token IS 'HMS Push Kit 设备级 push token（非 JWT）';
