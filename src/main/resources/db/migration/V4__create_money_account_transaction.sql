-- M2：零花钱账户（余额快照）+ 流水台账（M2-detailed-design.md §11.1）
-- 设计基线：余额快照 + 流水同事务双写；账户惰性开户（首笔入账触发）；
-- 乐观锁 version 条件更新；request_id 唯一索引（M3 幂等启用预留）。
-- 说明：金额统一 DECIMAL(12,2)（账户累计 14,2）；CHECK 约束为设计注释值的
-- 数据库级落地（方向/业务类型/状态/余额下限），较设计只增不减。
-- 时间类型统一写作 TIMESTAMP WITH TIME ZONE（H2 2.4.240 不识别 TIMESTAMPTZ
-- 缩写，约定同 V2）。

CREATE TABLE money_account (
    id            BIGSERIAL PRIMARY KEY,
    family_id     BIGINT NOT NULL REFERENCES family (id),
    user_id       BIGINT NOT NULL REFERENCES app_user (id),
    balance       DECIMAL(12, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0),
    total_income  DECIMAL(14, 2) NOT NULL DEFAULT 0,
    total_expense DECIMAL(14, 2) NOT NULL DEFAULT 0,
    status        VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                  CHECK (status IN ('ACTIVE', 'FROZEN')),
    version       BIGINT NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_money_account_user UNIQUE (user_id)
);

CREATE INDEX idx_money_account_family ON money_account (family_id);

COMMENT ON TABLE money_account IS '零花钱账户：一人一户、惰性开户、余额快照 + 乐观锁';
COMMENT ON COLUMN money_account.balance IS '当前余额，CHECK >= 0 兜底防透支';
COMMENT ON COLUMN money_account.version IS '乐观锁版本号，条件更新 WHERE version = ?';

CREATE TABLE money_transaction (
    id               BIGSERIAL PRIMARY KEY,
    family_id        BIGINT NOT NULL REFERENCES family (id),
    account_id       BIGINT NOT NULL REFERENCES money_account (id),
    user_id          BIGINT NOT NULL REFERENCES app_user (id),   -- 账户主人（冗余，免 join）
    direction        VARCHAR(8) NOT NULL CHECK (direction IN ('IN', 'OUT')),
    biz_type         VARCHAR(24) NOT NULL
                     CHECK (biz_type IN ('MONTHLY_RULE', 'MANUAL_ADD', 'LEARNING_REWARD',
                                         'WORK_VALUE', 'WITHDRAW')),
    amount           DECIMAL(12, 2) NOT NULL CHECK (amount > 0),
    balance_after    DECIMAL(12, 2) NOT NULL,
    ref_type         VARCHAR(24)
                     CHECK (ref_type IN ('RULE_GRANT', 'LEARNING_TASK', 'WORK_VALUE_RECORD')),
    ref_id           BIGINT,
    operator_user_id BIGINT REFERENCES app_user (id),             -- 定时结算为 NULL
    remark           VARCHAR(128),
    request_id       VARCHAR(64),                                 -- M3 幂等键预留（D12）
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_mtxn_family_time  ON money_transaction (family_id, created_at);
CREATE INDEX idx_mtxn_account_time ON money_transaction (account_id, created_at);
CREATE INDEX idx_mtxn_ref          ON money_transaction (ref_type, ref_id);
-- M3 幂等启用预留：非空 request_id 全局唯一（NULL 不受约束）
-- 设计原为部分唯一索引（WHERE request_id IS NOT NULL），H2 不支持部分索引；
-- PostgreSQL 与 H2 唯一索引均允许多个 NULL，普通唯一索引语义等价
CREATE UNIQUE INDEX uk_mtxn_request ON money_transaction (request_id);

COMMENT ON TABLE money_transaction IS '零花钱流水台账：只追加（append-only），balance_after 为记账后余额';
