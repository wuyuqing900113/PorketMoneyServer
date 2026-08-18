-- ============================================================
-- V2：用户与家庭域（M1 设计 §7.1）
-- 时间类型统一写作 TIMESTAMP WITH TIME ZONE（PostgreSQL 标准写法，
-- H2 PostgreSQL 兼容模式亦支持；等价于 PG 的 TIMESTAMPTZ 别名，
-- 保证开发托底库与生产库方言一致 —— spike 实测 H2 2.4.240 不识别
-- TIMESTAMPTZ 缩写）。
-- 脚本一经提交永不修改；回滚走新脚本前向修复（M0 既定规范）。
-- ============================================================

CREATE TABLE app_user (
    id                     BIGSERIAL PRIMARY KEY,
    username               VARCHAR(20),              -- 孩子登录名（全局唯一）；家长为 NULL
    phone_hash             CHAR(64),                 -- 家长手机号 SHA-256 hex（查找/唯一）；孩子为 NULL
    phone_encrypted        VARCHAR(512),             -- 家长手机号 AES-256-GCM Base64（回显用）
    key_version            SMALLINT  NOT NULL DEFAULT 1,   -- 加密密钥版本（轮换预留，§8.3）
    password_hash          VARCHAR(72) NOT NULL,     -- BCrypt(60)
    nickname               VARCHAR(32) NOT NULL,
    role                   VARCHAR(16) NOT NULL,     -- PARENT / CHILD
    status                 VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / DISABLED
    must_change_password   BOOLEAN   NOT NULL DEFAULT FALSE,
    consented_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),  -- 儿童隐私政策同意留痕
    consented_by           BIGINT,                    -- 孩子：创建其账号的家长 id；家长：NULL
    failed_attempts        SMALLINT  NOT NULL DEFAULT 0,
    locked_until           TIMESTAMP WITH TIME ZONE,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_app_user_username  UNIQUE (username),
    CONSTRAINT uk_app_user_phone     UNIQUE (phone_hash),
    CONSTRAINT chk_app_user_identifier CHECK (
        (username IS NOT NULL AND phone_hash IS NULL)
     OR (username IS NULL AND phone_hash IS NOT NULL))
);

CREATE TABLE family (
    id            BIGSERIAL PRIMARY KEY,
    family_name   VARCHAR(32) NOT NULL,
    owner_user_id BIGINT NOT NULL REFERENCES app_user (id),
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE family_member (
    id        BIGSERIAL PRIMARY KEY,
    family_id BIGINT NOT NULL REFERENCES family (id),
    user_id   BIGINT NOT NULL REFERENCES app_user (id),
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_family_member_family UNIQUE (family_id, user_id),
    CONSTRAINT uk_family_member_user   UNIQUE (user_id)      -- M1：一人一家庭
);
