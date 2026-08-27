-- M2：包月规则 + 发放记录（M2-detailed-design.md §11.2）
-- 设计基线：grant_day 限 1–28 规避月末越界；每日结算“当月应发未发即补”，
-- 幂等锚点 = uk(rule_id, grant_month)；(family_id, rule_name) 唯一防重名。

CREATE TABLE money_rule (
    id                  BIGSERIAL PRIMARY KEY,
    family_id           BIGINT NOT NULL REFERENCES family (id),
    beneficiary_user_id BIGINT NOT NULL REFERENCES app_user (id),
    rule_name           VARCHAR(32) NOT NULL,
    amount              DECIMAL(12, 2) NOT NULL CHECK (amount > 0),
    grant_day           SMALLINT NOT NULL CHECK (grant_day BETWEEN 1 AND 28),
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE', 'PAUSED', 'ARCHIVED')),
    start_month         CHAR(7) NOT NULL,                        -- YYYY-MM
    end_month           CHAR(7),                                 -- NULL = 长期
    remark              VARCHAR(128),
    created_by          BIGINT NOT NULL REFERENCES app_user (id),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_rule_family_name UNIQUE (family_id, rule_name)
);

CREATE INDEX idx_rule_settle_scan ON money_rule (status, grant_day);   -- 结算扫描
CREATE INDEX idx_rule_beneficiary ON money_rule (beneficiary_user_id);

COMMENT ON TABLE money_rule IS '包月零花钱规则：按月定额发放，grant_day 1–28';
COMMENT ON COLUMN money_rule.start_month IS '生效起始月，YYYY-MM';
COMMENT ON COLUMN money_rule.end_month IS '失效月（含），NULL 表示长期';

-- 结算幂等锚点（§7.2）：同规则同月仅一条
CREATE TABLE rule_grant_record (
    id             BIGSERIAL PRIMARY KEY,
    rule_id        BIGINT NOT NULL REFERENCES money_rule (id),
    grant_month    CHAR(7) NOT NULL,
    amount         DECIMAL(12, 2) NOT NULL,
    transaction_id BIGINT REFERENCES money_transaction (id),
    status         VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
    granted_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT uk_grant_rule_month UNIQUE (rule_id, grant_month)
);

COMMENT ON TABLE rule_grant_record IS '规则发放记录：(rule_id, grant_month) 唯一 = 结算幂等锚点';
