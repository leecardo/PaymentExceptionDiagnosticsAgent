-- 001_payment_diagnosis_scenarios.sql
-- 支付异常诊断脱敏演示数据。
--
-- 前置条件：必须先执行 V2__create_diagnostic_fact_tables.sql
-- 执行方式：手动对 PostgreSQL 数据库执行
--   psql -f deploy/postgres/demo/001_payment_diagnosis_scenarios.sql
--
-- 幂等：使用 INSERT ... ON CONFLICT DO UPDATE，重复执行结果一致。
-- 所有数据均为合成脱敏数据，不包含真实姓名、手机号、地址、令牌、密钥或供应商凭据。
-- 业务 ID 和事实语义与 simulation JSON 场景一致。

BEGIN;

-- 订单表（父表，必须先于依赖事实插入） — must be inserted before dependent facts)

INSERT INTO orders (order_id, master_order_id, role, product_id, product_name, product_type,
    goods_count, unit_price, order_amount, payment_amount, payment_source, provider_order_id,
    order_source, status, ordered_at, state_changed_at, created_at, updated_at)
VALUES
    ('SIM-NORMAL-001', NULL, 'SINGLE', 'PROD-SIM-001', 'Synthetic normal package', 'DIGITAL_SERVICE',
     1, 42.00, 42.00, 42.00, 'SIM_CHECKOUT', 'PROV-SIM-NORMAL-001', 'SIM_PORTAL', 'PAID',
     '2026-08-17T11:00:00Z', '2026-08-17T11:06:00Z', '2026-08-17T11:00:00Z', '2026-08-17T11:06:00Z'),
    ('SIM-PAY-NOT-STARTED-001', NULL, 'SINGLE', 'PROD-SIM-002', 'Synthetic pending package', 'DIGITAL_SERVICE',
     1, 30.00, 30.00, 30.00, 'SIM_CHECKOUT', NULL, 'SIM_PORTAL', 'PENDING_PAYMENT',
     '2026-08-17T11:10:00Z', '2026-08-17T11:10:00Z', '2026-08-17T11:10:00Z', '2026-08-17T11:10:00Z'),
    ('SIM-PAY-TIMEOUT-001', NULL, 'SINGLE', 'PROD-SIM-003', 'Synthetic processing package', 'DIGITAL_SERVICE',
     1, 31.00, 31.00, 31.00, 'SIM_CHECKOUT', 'PROV-SIM-PAY-TIMEOUT-001', 'SIM_PORTAL', 'PENDING_PAYMENT',
     '2026-08-17T11:38:00Z', '2026-08-17T11:38:00Z', '2026-08-17T11:38:00Z', '2026-08-17T11:38:00Z'),
    ('SIM-CALLBACK-MISSING-001', NULL, 'SINGLE', 'PROD-SIM-004', 'Synthetic callback package', 'DIGITAL_SERVICE',
     1, 32.00, 32.00, 32.00, 'SIM_CHECKOUT', 'PROV-SIM-CALLBACK-MISSING-001', 'SIM_PORTAL', 'PENDING_PAYMENT',
     '2026-08-17T11:18:00Z', '2026-08-17T11:18:00Z', '2026-08-17T11:18:00Z', '2026-08-17T11:18:00Z'),
    ('SIM-ORDER-NOT-UPDATED-001', NULL, 'SINGLE', 'PROD-SIM-005', 'Synthetic stale order package', 'DIGITAL_SERVICE',
     1, 33.00, 33.00, 33.00, 'SIM_CHECKOUT', 'PROV-SIM-ORDER-NOT-UPDATED-001', 'SIM_PORTAL', 'PENDING_PAYMENT',
     '2026-08-17T11:19:00Z', '2026-08-17T11:19:00Z', '2026-08-17T11:19:00Z', '2026-08-17T11:19:00Z'),
    ('SIM-PROVIDER-FAILED-001', NULL, 'SINGLE', 'PROD-SIM-006', 'Synthetic provider failure package', 'DIGITAL_SERVICE',
     1, 34.00, 34.00, 34.00, 'SIM_CHECKOUT', 'PROV-SIM-PROVIDER-FAILED-001', 'SIM_PORTAL', 'PENDING_PAYMENT',
     '2026-08-17T11:21:00Z', '2026-08-17T11:21:00Z', '2026-08-17T11:21:00Z', '2026-08-17T11:21:00Z'),
    ('SIM-MESSAGE-NOT-SENT-001', NULL, 'SINGLE', 'PROD-SIM-007', 'Synthetic message package', 'DIGITAL_SERVICE',
     1, 35.00, 35.00, 35.00, 'SIM_CHECKOUT', 'PROV-SIM-MESSAGE-NOT-SENT-001', 'SIM_PORTAL', 'PAID',
     '2026-08-17T11:22:00Z', '2026-08-17T11:28:00Z', '2026-08-17T11:22:00Z', '2026-08-17T11:28:00Z'),
    ('SIM-MESSAGE-SEND-FAILED-001', NULL, 'SINGLE', 'PROD-SIM-008', 'Synthetic send failure package', 'DIGITAL_SERVICE',
     1, 36.00, 36.00, 36.00, 'SIM_CHECKOUT', 'PROV-SIM-MESSAGE-SEND-FAILED-001', 'SIM_PORTAL', 'PAID',
     '2026-08-17T11:24:00Z', '2026-08-17T11:30:00Z', '2026-08-17T11:24:00Z', '2026-08-17T11:30:00Z'),
    ('SIM-MESSAGE-NOT-CONSUMED-001', NULL, 'SINGLE', 'PROD-SIM-009', 'Synthetic consumption package', 'DIGITAL_SERVICE',
     1, 37.00, 37.00, 37.00, 'SIM_CHECKOUT', 'PROV-SIM-MESSAGE-NOT-CONSUMED-001', 'SIM_PORTAL', 'PAID',
     '2026-08-17T11:25:00Z', '2026-08-17T11:31:00Z', '2026-08-17T11:25:00Z', '2026-08-17T11:31:00Z'),
    ('SIM-MESSAGE-CONSUME-FAILED-001', NULL, 'SINGLE', 'PROD-SIM-010', 'Synthetic consumer failure package', 'DIGITAL_SERVICE',
     1, 38.00, 38.00, 38.00, 'SIM_CHECKOUT', 'PROV-SIM-MESSAGE-CONSUME-FAILED-001', 'SIM_PORTAL', 'PAID',
     '2026-08-17T11:26:00Z', '2026-08-17T11:32:00Z', '2026-08-17T11:26:00Z', '2026-08-17T11:32:00Z'),
    ('SIM-COMP-NOT-CREATED-001', NULL, 'SINGLE', 'PROD-SIM-011', 'Synthetic compensation package', 'DIGITAL_SERVICE',
     1, 39.00, 39.00, 39.00, 'SIM_CHECKOUT', 'PROV-SIM-COMP-NOT-CREATED-001', 'SIM_PORTAL', 'CANCELLED',
     '2026-08-17T11:27:00Z', '2026-08-17T11:34:00Z', '2026-08-17T11:27:00Z', '2026-08-17T11:34:00Z'),
    ('SIM-COMP-FAILED-001', NULL, 'SINGLE', 'PROD-SIM-012', 'Synthetic retry package', 'DIGITAL_SERVICE',
     1, 40.00, 40.00, 40.00, 'SIM_CHECKOUT', 'PROV-SIM-COMP-FAILED-001', 'SIM_PORTAL', 'CANCELLED',
     '2026-08-17T11:28:00Z', '2026-08-17T11:35:00Z', '2026-08-17T11:28:00Z', '2026-08-17T11:35:00Z'),
    ('SIM-COMP-EXHAUSTED-001', NULL, 'SINGLE', 'PROD-SIM-013', 'Synthetic exhausted retry package', 'DIGITAL_SERVICE',
     1, 41.00, 41.00, 41.00, 'SIM_CHECKOUT', 'PROV-SIM-COMP-EXHAUSTED-001', 'SIM_PORTAL', 'CANCELLED',
     '2026-08-17T11:29:00Z', '2026-08-17T11:36:00Z', '2026-08-17T11:29:00Z', '2026-08-17T11:36:00Z'),
    ('SIM-TRACE-MISSING-INSUFFICIENT-001', NULL, 'SINGLE', 'PROD-SIM-014', 'Synthetic trace gap package', 'DIGITAL_SERVICE',
     1, 43.00, 43.00, 43.00, 'SIM_CHECKOUT', NULL, 'SIM_PORTAL', 'OUTBOUND',
     '2026-08-17T11:30:00Z', '2026-08-17T11:37:00Z', '2026-08-17T11:30:00Z', '2026-08-17T11:37:00Z')
ON CONFLICT (order_id) DO UPDATE SET
    master_order_id = EXCLUDED.master_order_id,
    role = EXCLUDED.role,
    product_id = EXCLUDED.product_id,
    product_name = EXCLUDED.product_name,
    product_type = EXCLUDED.product_type,
    goods_count = EXCLUDED.goods_count,
    unit_price = EXCLUDED.unit_price,
    order_amount = EXCLUDED.order_amount,
    payment_amount = EXCLUDED.payment_amount,
    payment_source = EXCLUDED.payment_source,
    provider_order_id = EXCLUDED.provider_order_id,
    order_source = EXCLUDED.order_source,
    status = EXCLUDED.status,
    ordered_at = EXCLUDED.ordered_at,
    state_changed_at = EXCLUDED.state_changed_at,
    created_at = EXCLUDED.created_at,
    updated_at = EXCLUDED.updated_at;

-- 支付流水表

INSERT INTO payment_transactions (transaction_id, order_id, provider, amount, status,
    requested_at, provider_completed_at, callback_received_at, provider_error_code, provider_error_summary)
VALUES
    ('PAY-SIM-NORMAL-001', 'SIM-NORMAL-001', 'SIM_PROVIDER', 42.00, 'CALLBACK_RECEIVED',
     '2026-08-17T11:01:00Z', '2026-08-17T11:04:00Z', '2026-08-17T11:06:00Z', NULL, NULL),
    ('PAY-SIM-TIMEOUT-001', 'SIM-PAY-TIMEOUT-001', 'SIM_PROVIDER', 31.00, 'PROCESSING',
     '2026-08-17T11:40:00Z', NULL, NULL, NULL, NULL),
    ('PAY-SIM-CALLBACK-MISSING-001', 'SIM-CALLBACK-MISSING-001', 'SIM_PROVIDER', 32.00, 'PROVIDER_SUCCEEDED',
     '2026-08-17T11:20:00Z', '2026-08-17T11:25:00Z', NULL, NULL, NULL),
    ('PAY-SIM-ORDER-NOT-UPDATED-001', 'SIM-ORDER-NOT-UPDATED-001', 'SIM_PROVIDER', 33.00, 'CALLBACK_RECEIVED',
     '2026-08-17T11:21:00Z', '2026-08-17T11:24:00Z', '2026-08-17T11:26:00Z', NULL, NULL),
    ('PAY-SIM-PROVIDER-FAILED-001', 'SIM-PROVIDER-FAILED-001', 'SIM_PROVIDER', 34.00, 'FAILED',
     '2026-08-17T11:23:00Z', '2026-08-17T11:24:00Z', NULL, 'SIM_DECLINED', 'Synthetic provider declined payment'),
    ('PAY-SIM-MESSAGE-NOT-SENT-001', 'SIM-MESSAGE-NOT-SENT-001', 'SIM_PROVIDER', 35.00, 'CALLBACK_RECEIVED',
     '2026-08-17T11:23:00Z', '2026-08-17T11:26:00Z', '2026-08-17T11:28:00Z', NULL, NULL),
    ('PAY-SIM-MESSAGE-SEND-FAILED-001', 'SIM-MESSAGE-SEND-FAILED-001', 'SIM_PROVIDER', 36.00, 'CALLBACK_RECEIVED',
     '2026-08-17T11:25:00Z', '2026-08-17T11:28:00Z', '2026-08-17T11:30:00Z', NULL, NULL),
    ('PAY-SIM-MESSAGE-NOT-CONSUMED-001', 'SIM-MESSAGE-NOT-CONSUMED-001', 'SIM_PROVIDER', 37.00, 'CALLBACK_RECEIVED',
     '2026-08-17T11:26:00Z', '2026-08-17T11:29:00Z', '2026-08-17T11:31:00Z', NULL, NULL),
    ('PAY-SIM-MESSAGE-CONSUME-FAILED-001', 'SIM-MESSAGE-CONSUME-FAILED-001', 'SIM_PROVIDER', 38.00, 'CALLBACK_RECEIVED',
     '2026-08-17T11:27:00Z', '2026-08-17T11:30:00Z', '2026-08-17T11:32:00Z', NULL, NULL),
    ('PAY-SIM-COMP-NOT-CREATED-001', 'SIM-COMP-NOT-CREATED-001', 'SIM_PROVIDER', 39.00, 'CALLBACK_RECEIVED',
     '2026-08-17T11:29:00Z', '2026-08-17T11:32:00Z', '2026-08-17T11:34:00Z', NULL, NULL),
    ('PAY-SIM-COMP-FAILED-001', 'SIM-COMP-FAILED-001', 'SIM_PROVIDER', 40.00, 'CALLBACK_RECEIVED',
     '2026-08-17T11:30:00Z', '2026-08-17T11:33:00Z', '2026-08-17T11:35:00Z', NULL, NULL),
    ('PAY-SIM-COMP-EXHAUSTED-001', 'SIM-COMP-EXHAUSTED-001', 'SIM_PROVIDER', 41.00, 'CALLBACK_RECEIVED',
     '2026-08-17T11:31:00Z', '2026-08-17T11:34:00Z', '2026-08-17T11:36:00Z', NULL, NULL)
ON CONFLICT (transaction_id) DO UPDATE SET
    order_id = EXCLUDED.order_id,
    provider = EXCLUDED.provider,
    amount = EXCLUDED.amount,
    status = EXCLUDED.status,
    requested_at = EXCLUDED.requested_at,
    provider_completed_at = EXCLUDED.provider_completed_at,
    callback_received_at = EXCLUDED.callback_received_at,
    provider_error_code = EXCLUDED.provider_error_code,
    provider_error_summary = EXCLUDED.provider_error_summary;

-- 消息投递表

INSERT INTO message_deliveries (delivery_id, order_id, event_type, correlation_id, status,
    created_at, sent_at, consumed_at, last_error)
VALUES
    ('MSG-SIM-NORMAL-001', 'SIM-NORMAL-001', 'PAYMENT_CONFIRMED', 'CORR-SIM-NORMAL-001', 'CONSUMED',
     '2026-08-17T11:06:30Z', '2026-08-17T11:07:00Z', '2026-08-17T11:08:00Z', NULL),
    ('MSG-SIM-SEND-FAILED-001', 'SIM-MESSAGE-SEND-FAILED-001', 'PAYMENT_CONFIRMED', 'CORR-SIM-MESSAGE-SEND-FAILED-001', 'SEND_FAILED',
     '2026-08-17T11:30:30Z', NULL, NULL, 'Synthetic broker unavailable'),
    ('MSG-SIM-NOT-CONSUMED-001', 'SIM-MESSAGE-NOT-CONSUMED-001', 'PAYMENT_CONFIRMED', 'CORR-SIM-MESSAGE-NOT-CONSUMED-001', 'SENT',
     '2026-08-17T11:31:30Z', '2026-08-17T11:40:00Z', NULL, NULL),
    ('MSG-SIM-CONSUME-FAILED-001', 'SIM-MESSAGE-CONSUME-FAILED-001', 'PAYMENT_CONFIRMED', 'CORR-SIM-MESSAGE-CONSUME-FAILED-001', 'CONSUME_FAILED',
     '2026-08-17T11:32:30Z', '2026-08-17T11:33:00Z', NULL, 'Synthetic consumer unavailable')
ON CONFLICT (delivery_id) DO UPDATE SET
    order_id = EXCLUDED.order_id,
    event_type = EXCLUDED.event_type,
    correlation_id = EXCLUDED.correlation_id,
    status = EXCLUDED.status,
    created_at = EXCLUDED.created_at,
    sent_at = EXCLUDED.sent_at,
    consumed_at = EXCLUDED.consumed_at,
    last_error = EXCLUDED.last_error;

-- 补偿任务表

INSERT INTO compensation_tasks (task_id, order_id, action, status, retry_count, max_retries,
    created_at, last_attempt_at, last_error)
VALUES
    ('COMP-SIM-FAILED-001', 'SIM-COMP-FAILED-001', 'REVERSE_SYNTHETIC_PAYMENT', 'FAILED',
     1, 3, '2026-08-17T11:36:00Z', '2026-08-17T11:42:00Z', 'Synthetic reversal service unavailable'),
    ('COMP-SIM-EXHAUSTED-001', 'SIM-COMP-EXHAUSTED-001', 'REVERSE_SYNTHETIC_PAYMENT', 'RETRIES_EXHAUSTED',
     3, 3, '2026-08-17T11:37:00Z', '2026-08-17T11:43:00Z', 'Synthetic reversal retries exhausted')
ON CONFLICT (task_id) DO UPDATE SET
    order_id = EXCLUDED.order_id,
    action = EXCLUDED.action,
    status = EXCLUDED.status,
    retry_count = EXCLUDED.retry_count,
    max_retries = EXCLUDED.max_retries,
    created_at = EXCLUDED.created_at,
    last_attempt_at = EXCLUDED.last_attempt_at,
    last_error = EXCLUDED.last_error;

-- 调用链路摘要表

INSERT INTO trace_summaries (trace_id, order_id, correlation_id, started_at, ended_at, complete, summary)
VALUES
    ('TRACE-SIM-NORMAL-001', 'SIM-NORMAL-001', 'CORR-SIM-NORMAL-001',
     '2026-08-17T11:00:00Z', '2026-08-17T11:09:00Z', true, 'Synthetic complete payment flow'),
    ('TRACE-SIM-PAY-NOT-STARTED-001', 'SIM-PAY-NOT-STARTED-001', 'CORR-SIM-PAY-NOT-STARTED-001',
     '2026-08-17T11:10:00Z', '2026-08-17T11:11:00Z', false, 'Synthetic order created before payment attempt'),
    ('TRACE-SIM-PAY-TIMEOUT-001', 'SIM-PAY-TIMEOUT-001', 'CORR-SIM-PAY-TIMEOUT-001',
     '2026-08-17T11:38:00Z', '2026-08-17T11:41:00Z', false, 'Synthetic payment still processing'),
    ('TRACE-SIM-CALLBACK-MISSING-001', 'SIM-CALLBACK-MISSING-001', 'CORR-SIM-CALLBACK-MISSING-001',
     '2026-08-17T11:18:00Z', '2026-08-17T11:25:00Z', false, 'Synthetic provider success without callback'),
    ('TRACE-SIM-ORDER-NOT-UPDATED-001', 'SIM-ORDER-NOT-UPDATED-001', 'CORR-SIM-ORDER-NOT-UPDATED-001',
     '2026-08-17T11:19:00Z', '2026-08-17T11:26:00Z', false, 'Synthetic callback recorded before order state update'),
    ('TRACE-SIM-PROVIDER-FAILED-001', 'SIM-PROVIDER-FAILED-001', 'CORR-SIM-PROVIDER-FAILED-001',
     '2026-08-17T11:21:00Z', '2026-08-17T11:24:00Z', false, 'Synthetic provider declined payment'),
    ('TRACE-SIM-MESSAGE-NOT-SENT-001', 'SIM-MESSAGE-NOT-SENT-001', 'CORR-SIM-MESSAGE-NOT-SENT-001',
     '2026-08-17T11:22:00Z', '2026-08-17T11:28:00Z', false, 'Synthetic payment callback without delivery fact'),
    ('TRACE-SIM-MESSAGE-SEND-FAILED-001', 'SIM-MESSAGE-SEND-FAILED-001', 'CORR-SIM-MESSAGE-SEND-FAILED-001',
     '2026-08-17T11:24:00Z', '2026-08-17T11:30:30Z', false, 'Synthetic delivery send failure'),
    ('TRACE-SIM-MESSAGE-NOT-CONSUMED-001', 'SIM-MESSAGE-NOT-CONSUMED-001', 'CORR-SIM-MESSAGE-NOT-CONSUMED-001',
     '2026-08-17T11:25:00Z', '2026-08-17T11:40:00Z', false, 'Synthetic message awaiting consumption'),
    ('TRACE-SIM-MESSAGE-CONSUME-FAILED-001', 'SIM-MESSAGE-CONSUME-FAILED-001', 'CORR-SIM-MESSAGE-CONSUME-FAILED-001',
     '2026-08-17T11:26:00Z', '2026-08-17T11:33:00Z', false, 'Synthetic message consumption failure'),
    ('TRACE-SIM-COMP-NOT-CREATED-001', 'SIM-COMP-NOT-CREATED-001', 'CORR-SIM-COMP-NOT-CREATED-001',
     '2026-08-17T11:27:00Z', '2026-08-17T11:34:00Z', false, 'Synthetic cancelled order awaiting compensation'),
    ('TRACE-SIM-COMP-FAILED-001', 'SIM-COMP-FAILED-001', 'CORR-SIM-COMP-FAILED-001',
     '2026-08-17T11:28:00Z', '2026-08-17T11:42:00Z', false, 'Synthetic compensation retry failure'),
    ('TRACE-SIM-COMP-EXHAUSTED-001', 'SIM-COMP-EXHAUSTED-001', 'CORR-SIM-COMP-EXHAUSTED-001',
     '2026-08-17T11:29:00Z', '2026-08-17T11:43:00Z', false, 'Synthetic compensation retries exhausted')
ON CONFLICT (trace_id) DO UPDATE SET
    order_id = EXCLUDED.order_id,
    correlation_id = EXCLUDED.correlation_id,
    started_at = EXCLUDED.started_at,
    ended_at = EXCLUDED.ended_at,
    complete = EXCLUDED.complete,
    summary = EXCLUDED.summary;

COMMIT;
