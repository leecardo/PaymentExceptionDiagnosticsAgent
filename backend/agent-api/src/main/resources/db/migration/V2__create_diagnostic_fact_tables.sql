-- V2__create_diagnostic_fact_tables.sql
-- 创建支付异常诊断事实表。
-- 前置条件：V1__enable_vector_extension.sql
-- 表结构使用 PostgreSQL 原生类型、命名约束和命名索引。
-- 所有 CHECK 约束与 Java 领域枚举完全一致，防止非法状态写入。
-- 不包含客户身份或配送地址等敏感字段。

-- 订单表：脱敏订单快照事实（无客户身份、地址、联系方式字段）
CREATE TABLE IF NOT EXISTS orders (
    order_id            varchar(64)   NOT NULL,
    master_order_id     varchar(64),
    role                varchar(8)   NOT NULL,
    product_id          varchar(64)  NOT NULL,
    product_name        varchar(256) NOT NULL,
    product_type        varchar(64)  NOT NULL,
    goods_count         integer      NOT NULL,
    unit_price          numeric(10,2) NOT NULL,
    order_amount        numeric(10,2) NOT NULL,
    payment_amount      numeric(10,2) NOT NULL,
    payment_source      varchar(64),
    provider_order_id   varchar(128),
    order_source        varchar(64)  NOT NULL,
    status              varchar(20)  NOT NULL,
    ordered_at          timestamptz  NOT NULL,
    state_changed_at    timestamptz,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    CONSTRAINT pk_orders PRIMARY KEY (order_id),
    -- 角色约束：单订单 SINGLE、主订单 MASTER、子订单 SUB
    CONSTRAINT chk_orders_role CHECK (role IN ('SINGLE', 'MASTER', 'SUB')),
    -- 订单状态约束：对应 prod_order_user.ORDER_STATE 取值
    CONSTRAINT chk_orders_status CHECK (status IN (
        'PENDING_PAYMENT', 'CANCELLED', 'PAID', 'OUTBOUND',
        'SHIPPED', 'SIGNED', 'COMPLETED', 'CLOSED'
    )),
    -- 商品数量必须大于零
    CONSTRAINT chk_orders_goods_count CHECK (goods_count > 0),
    -- 金额不得为负
    CONSTRAINT chk_orders_amounts_nonneg CHECK (
        unit_price >= 0 AND order_amount >= 0 AND payment_amount >= 0
    ),
    -- 更新时间不得早于创建时间
    CONSTRAINT chk_orders_updated_after_created CHECK (updated_at >= created_at),
    -- 子订单必须有主订单号；单订单和主订单不得携带主订单号
    CONSTRAINT chk_orders_sub_has_master CHECK (
        (role = 'SUB' AND master_order_id IS NOT NULL)
        OR (role IN ('SINGLE', 'MASTER') AND master_order_id IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_orders_master_order_id ON orders (master_order_id) WHERE master_order_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders (status);

-- 支付流水表：第三方支付和回调事实
CREATE TABLE IF NOT EXISTS payment_transactions (
    transaction_id          varchar(64)   NOT NULL,
    order_id                varchar(64)   NOT NULL,
    provider                varchar(64)   NOT NULL,
    amount                  numeric(10,2) NOT NULL,
    status                  varchar(20)   NOT NULL,
    requested_at            timestamptz   NOT NULL,
    provider_completed_at   timestamptz,
    callback_received_at    timestamptz,
    provider_error_code     varchar(64),
    provider_error_summary  varchar(256),
    CONSTRAINT pk_payment_transactions PRIMARY KEY (transaction_id),
    -- 外键引用订单表，禁止级联删除
    CONSTRAINT fk_payment_transactions_orders FOREIGN KEY (order_id)
        REFERENCES orders (order_id) ON DELETE RESTRICT,
    -- 支付状态约束
    CONSTRAINT chk_payment_status CHECK (status IN (
        'REQUESTED', 'PROCESSING', 'PROVIDER_SUCCEEDED',
        'CALLBACK_RECEIVED', 'FAILED'
    )),
    -- 金额不得为负
    CONSTRAINT chk_payment_amount_nonneg CHECK (amount >= 0),
    -- 供应商完成时间不得早于请求时间
    CONSTRAINT chk_payment_provider_after_requested CHECK (
        provider_completed_at IS NULL OR provider_completed_at >= requested_at
    ),
    -- 回调时间不得早于请求时间
    CONSTRAINT chk_payment_callback_after_requested CHECK (
        callback_received_at IS NULL OR callback_received_at >= requested_at
    ),
    -- 回调时间不得早于供应商完成时间
    CONSTRAINT chk_payment_callback_after_provider CHECK (
        callback_received_at IS NULL OR provider_completed_at IS NULL
        OR callback_received_at >= provider_completed_at
    )
);

-- 按订单号查询支付流水的索引
CREATE INDEX IF NOT EXISTS idx_payment_transactions_order_id ON payment_transactions (order_id);

-- 消息投递表：逻辑发送和消费事实
CREATE TABLE IF NOT EXISTS message_deliveries (
    delivery_id      varchar(64)  NOT NULL,
    order_id         varchar(64)  NOT NULL,
    event_type       varchar(64)  NOT NULL,
    correlation_id   varchar(64)  NOT NULL,
    status           varchar(20)  NOT NULL,
    created_at       timestamptz  NOT NULL,
    sent_at          timestamptz,
    consumed_at       timestamptz,
    last_error        varchar(256),
    CONSTRAINT pk_message_deliveries PRIMARY KEY (delivery_id),
    CONSTRAINT fk_message_deliveries_orders FOREIGN KEY (order_id)
        REFERENCES orders (order_id) ON DELETE RESTRICT,
    -- 消息投递状态约束
    CONSTRAINT chk_message_status CHECK (status IN (
        'PENDING', 'SENT', 'SEND_FAILED', 'CONSUMED', 'CONSUME_FAILED'
    )),
    -- 发送时间不得早于创建时间
    CONSTRAINT chk_message_sent_after_created CHECK (
        sent_at IS NULL OR sent_at >= created_at
    ),
    -- 消费时间要求先发送，且不得早于发送时间
    CONSTRAINT chk_message_consumed_after_sent CHECK (
        consumed_at IS NULL OR (sent_at IS NOT NULL AND consumed_at >= sent_at)
    )
);

CREATE INDEX IF NOT EXISTS idx_message_deliveries_order_id ON message_deliveries (order_id);
CREATE INDEX IF NOT EXISTS idx_message_deliveries_correlation_id ON message_deliveries (correlation_id);

-- 补偿任务表：有界重试事实
CREATE TABLE IF NOT EXISTS compensation_tasks (
    task_id          varchar(64)  NOT NULL,
    order_id        varchar(64)  NOT NULL,
    action           varchar(64)  NOT NULL,
    status           varchar(20)  NOT NULL,
    retry_count      integer      NOT NULL,
    max_retries      integer      NOT NULL,
    created_at       timestamptz  NOT NULL,
    last_attempt_at  timestamptz,
    last_error        varchar(256),
    CONSTRAINT pk_compensation_tasks PRIMARY KEY (task_id),
    CONSTRAINT fk_compensation_tasks_orders FOREIGN KEY (order_id)
        REFERENCES orders (order_id) ON DELETE RESTRICT,
    -- 补偿任务状态约束
    CONSTRAINT chk_compensation_status CHECK (status IN (
        'PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'RETRIES_EXHAUSTED'
    )),
    -- 重试次数不得为负
    CONSTRAINT chk_compensation_retry_nonneg CHECK (retry_count >= 0 AND max_retries >= 0),
    -- 重试次数不得超过最大重试次数
    CONSTRAINT chk_compensation_retry_bound CHECK (retry_count <= max_retries),
    -- 最后尝试时间不得早于创建时间
    CONSTRAINT chk_compensation_last_attempt_after_created CHECK (
        last_attempt_at IS NULL OR last_attempt_at >= created_at
    ),
    -- 重试耗尽状态要求：重试次数=最大重试次数且必须有错误信息
    CONSTRAINT chk_compensation_exhausted CHECK (
        status <> 'RETRIES_EXHAUSTED' OR (retry_count = max_retries AND last_error IS NOT NULL)
    ),
    -- 失败状态要求：必须有错误信息
    CONSTRAINT chk_compensation_failed_has_error CHECK (
        status <> 'FAILED' OR last_error IS NOT NULL
    )
);

CREATE INDEX IF NOT EXISTS idx_compensation_tasks_order_id ON compensation_tasks (order_id);

-- 调用链路摘要表：Trace 可用性事实
CREATE TABLE IF NOT EXISTS trace_summaries (
    trace_id         varchar(64)  NOT NULL,
    order_id         varchar(64)  NOT NULL,
    correlation_id   varchar(64)  NOT NULL,
    started_at       timestamptz  NOT NULL,
    ended_at         timestamptz,
    complete         boolean      NOT NULL,
    summary          varchar(512) NOT NULL,
    CONSTRAINT pk_trace_summaries PRIMARY KEY (trace_id),
    CONSTRAINT fk_trace_summaries_orders FOREIGN KEY (order_id)
        REFERENCES orders (order_id) ON DELETE RESTRICT,
    -- 结束时间不得早于开始时间
    CONSTRAINT chk_trace_ended_after_started CHECK (
        ended_at IS NULL OR ended_at >= started_at
    )
);

CREATE INDEX IF NOT EXISTS idx_trace_summaries_order_id ON trace_summaries (order_id);
CREATE INDEX IF NOT EXISTS idx_trace_summaries_correlation_id ON trace_summaries (correlation_id);
