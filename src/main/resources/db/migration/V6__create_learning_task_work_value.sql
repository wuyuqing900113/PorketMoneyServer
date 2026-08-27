-- M2：学习任务 + 工作价值记录（M2-detailed-design.md §11.3）
-- 学习任务状态机：PENDING → SUBMITTED → APPROVED/REJECTED（驳回可重提），
-- 发放前（PENDING/SUBMITTED）可取消 → CANCELED。

CREATE TABLE learning_task (
    id               BIGSERIAL PRIMARY KEY,
    family_id        BIGINT NOT NULL REFERENCES family (id),
    title            VARCHAR(64) NOT NULL,
    reward_amount    DECIMAL(12, 2) NOT NULL CHECK (reward_amount > 0),
    assignee_user_id BIGINT NOT NULL REFERENCES app_user (id),
    status           VARCHAR(16) NOT NULL DEFAULT 'PENDING'
                     CHECK (status IN ('PENDING', 'SUBMITTED', 'APPROVED',
                                       'REJECTED', 'CANCELED')),
        -- PENDING / SUBMITTED / APPROVED / REJECTED / CANCELED
    deadline         DATE,
    submit_note      VARCHAR(256),
    submitted_at     TIMESTAMP WITH TIME ZONE,
    reviewed_by      BIGINT REFERENCES app_user (id),
    reviewed_at      TIMESTAMP WITH TIME ZONE,
    reject_reason    VARCHAR(256),
    transaction_id   BIGINT REFERENCES money_transaction (id),
    created_by       BIGINT NOT NULL REFERENCES app_user (id),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_ltask_family_time    ON learning_task (family_id, created_at);
CREATE INDEX idx_ltask_assignee_state ON learning_task (assignee_user_id, status);

COMMENT ON TABLE learning_task IS '学习任务：家长定义、孩子提交、家长确认发放';

CREATE TABLE work_value_record (
    id               BIGSERIAL PRIMARY KEY,
    family_id        BIGINT NOT NULL REFERENCES family (id),
    parent_user_id   BIGINT NOT NULL REFERENCES app_user (id),
    work_month       CHAR(7) NOT NULL,                      -- 归属月 YYYY-MM
    salary_income    DECIMAL(14, 2) NOT NULL DEFAULT 0 CHECK (salary_income >= 0),
    allowance_amount DECIMAL(12, 2) NOT NULL CHECK (allowance_amount > 0),
    work_summary     VARCHAR(256),
    transaction_id   BIGINT REFERENCES money_transaction (id),
    recorded_by      BIGINT NOT NULL REFERENCES app_user (id),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_work_value_family_month ON work_value_record (family_id, work_month);
CREATE INDEX idx_work_value_parent       ON work_value_record (parent_user_id, work_month);

COMMENT ON TABLE work_value_record IS '工作价值：父母工资收入记录 + 发放金额入账父母账户';
