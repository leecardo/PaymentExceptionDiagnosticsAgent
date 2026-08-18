# 支付异常诊断模拟流程设计

## 1. 目标

在本地暂不启动 PostgreSQL、消息队列等中间件的前提下，完成可运行、可验证的后端支付异常模拟流程，并同时准备未来部署可使用的 PostgreSQL 初始化脚本和脱敏演示数据。

模拟流程基于结构化事实和确定性规则，不使用固定诊断文本冒充真实能力。未来接入 PostgreSQL 或具体消息中间件时，应用用例和领域规则不变，只替换基础设施适配器。

## 2. 范围

### 已实现

- 订单、支付流水、消息投递、补偿任务和 Trace 摘要领域事实类型；
- 五类只读查询端口；
- 从版本化场景数据文件加载事实的内存适配器；
- 固定顺序的证据收集流程；
- 第一批确定性诊断规则（15 条）；
- 后端订单查询和诊断查询 API；
- PostgreSQL Flyway 建表、约束和索引脚本；
- 独立、可选的 PostgreSQL 脱敏演示数据脚本；
- 厂商无关的消息事件契约和拓扑描述文件；
- 领域、应用、适配器和 API 契约测试。

### 未实现

- 启动或连接 PostgreSQL、pgvector、Redis、RabbitMQ、RocketMQ、Kafka；
- 真实消息发布或消费；
- 前端订单或诊断页面；
- MCP 业务工具；
- LangChain4j Agent、模型推理或 RAG；
- 自动补偿执行。

## 3. 核心设计决策

### 3.1 端口不变，适配器切换

领域层和应用层只认识查询端口。运行方式由 Spring profile 决定：

- `simulation`：从仓库内的版本化场景文件加载内存适配器；
- 后续 `postgres`：通过参数化 SQL 查询 PostgreSQL；
- 默认 profile 不启用模拟诊断能力，避免把模拟结果误认为真实系统事实。

API、固定工作流和规则诊断器不判断当前适配器类型，也不包含模拟场景分支。

### 3.2 模拟事实不等于 Mock 结论

场景文件只声明可观察事实（订单状态、支付流水、回调时间、消息发送和消费状态、补偿重试次数、Trace 摘要）。诊断结果必须由规则诊断器运行后产生。

API 响应标识数据模式为 `SIMULATION`，证据来源可定位到具体场景事实。不得把预先写好的"异常原因"字段直接作为诊断结论。

### 3.3 暂不绑定消息中间件

使用 `message_deliveries` 事实模型表达消息是否创建、发送、消费和失败；使用厂商无关的事件契约描述事件名、版本、关联键和负载字段；使用厂商无关的拓扑描述表达逻辑 channel、生产者、消费者和失败处理意图。

## 4. 分层设计

### 4.1 领域层（agent-domain）

不可变领域类型：

| 类型 | 说明 |
|---|---|
| `OrderId` | 订单标识符，正则 `[A-Za-z0-9._-]{1,64}` |
| `OrderStatus` | 订单状态枚举，对应 `prod_order_user.ORDER_STATE` |
| `OrderRole` | 订单角色：SINGLE / MASTER / SUB |
| `OrderSnapshot` | 订单快照，含金额非负、数量>0、更新时间≥创建时间等不变量 |
| `PaymentTransaction` | 支付流水，FAILED 必须有错误码和摘要 |
| `PaymentStatus` | 支付状态：REQUESTED → PROCESSING → PROVIDER_SUCCEEDED → CALLBACK_RECEIVED / FAILED |
| `MessageDelivery` | 消息投递记录，各状态有时间戳和 lastError 约束 |
| `MessageDeliveryStatus` | PENDING / SENT / SEND_FAILED / CONSUMED / CONSUME_FAILED |
| `CompensationTask` | 补偿任务，重试次数≤最大重试，RETRIES_EXHAUSTED 要求 retryCount==maxRetries |
| `CompensationStatus` | PENDING / RUNNING / SUCCEEDED / FAILED / RETRIES_EXHAUSTED |
| `TraceSummary` | 调用链路摘要，traceId 非空，endedAt≥startedAt |
| `DiagnosisEvidence` | 诊断证据（id, source, summary, observedAt） |
| `DiagnosisResult` | 诊断结果（orderId, dataMode, stage, ruleId, summary, evidence, warnings） |
| `DiagnosisRuleId` | 15 条规则标识枚举 |
| `DiagnosisStage` | 诊断阶段枚举 |
| `DataMode` | SIMULATION / POSTGRES |

订单状态映射（`prod_order_user.ORDER_STATE`）：

| 原状态码 | 领域状态 |
|---:|---|
| 0 | `PENDING_PAYMENT` |
| 1 | `CANCELLED` |
| 2 | `PAID` |
| 3 | `OUTBOUND` |
| 4 | `SHIPPED` |
| 5 | `SIGNED` |
| 6 | `COMPLETED` |
| 7 | `CLOSED` |

### 4.2 应用层（agent-application）

只读查询端口：

| 端口 | 方法 |
|---|---|
| `OrderQueryPort` | `findById(OrderId) → Optional<OrderSnapshot>` |
| `PaymentQueryPort` | `findByOrderId(OrderId) → List<PaymentTransaction>` |
| `MessageQueryPort` | `findByOrderId(OrderId) → List<MessageDelivery>` |
| `CompensationQueryPort` | `findByOrderId(OrderId) → List<CompensationTask>` |
| `TraceQueryPort` | `findByOrderId(OrderId) → Optional<TraceSummary>` |

`FactQueryException` 区分 `UNAVAILABLE` 和 `TIMEOUT`，确保查询失败不被伪装成业务记录缺失。

用例：

| 用例 | 说明 |
|---|---|
| `GetOrderUseCase` | 构造 OrderId 并查询，找不到抛 `OrderNotFoundException` |
| `DiagnosePaymentExceptionUseCase` | 固定顺序收集五类事实，委托规则引擎 |

固定诊断流程：

1. 校验订单号；
2. 查询订单（不存在则短路返回 404）；
3. 查询关联支付流水；
4. 查询消息投递事实；
5. 查询补偿任务事实；
6. 查询 Trace 摘要；
7. 将每条事实转换为可追溯证据；
8. 按明确优先级运行确定性规则；
9. 返回阶段、规则编号、证据和数据模式。

### 4.3 确定性诊断规则

`DeterministicDiagnosisRules` 按优先级依次检查：

| 优先级 | 规则 | 说明 |
|---:|---|---|
| 1 | `PAYMENT_NOT_STARTED` | 订单待支付且无支付流水 |
| 2 | `PAYMENT_PROCESSING_TIMEOUT` | 支付仍处理中且超过阈值（age > threshold） |
| 3 | `PROVIDER_SUCCEEDED_CALLBACK_MISSING` | 供应商成功但回调未接收 |
| 4 | `CALLBACK_SUCCEEDED_ORDER_NOT_UPDATED` | 回调成功但订单状态未更新 |
| 5 | `PAYMENT_FAILED_WITH_PROVIDER_ERROR` | 支付失败且有明确渠道错误码 |
| 6 | `MESSAGE_NOT_SENT` | 应发送消息但记录不存在 |
| 7 | `MESSAGE_SEND_FAILED` | 消息发送失败 |
| 8 | `MESSAGE_NOT_CONSUMED` | 已发送但超时未消费 |
| 9 | `MESSAGE_CONSUME_FAILED` | 消费失败 |
| 10 | `COMPENSATION_RETRIES_EXHAUSTED` | 重试次数达到上限 |
| 11 | `COMPENSATION_FAILED` | 补偿失败但尚可重试 |
| 12 | `COMPENSATION_NOT_CREATED` | 符合补偿条件但无补偿任务 |
| 13 | `TRACE_MISSING` | 其他事实不足且 Trace 缺失 |
| 14 | `NO_KNOWN_EXCEPTION` | 完整正常链路 |
| 15 | `INSUFFICIENT_EVIDENCE` | 无规则具备足够事实 |

证据 ID 命名约定：`order:<orderId>`、`payment:<transactionId>`、`message:<deliveryId>`、`compensation:<taskId>`、`trace:<traceId>`。

超时边界：`age == threshold` 不算超时；`age > threshold` 算超时。

### 4.4 基础设施层（agent-infrastructure）

模拟适配器（仅 `simulation` profile 激活）：

| 组件 | 说明 |
|---|---|
| `SimulationConfiguration` | Spring 配置类，创建 FactStore 和五个端口 Bean |
| `SimulationScenarioLoader` | 从 JSON 资源加载场景，映射 DTO 到领域对象 |
| `SimulationScenarioDocument` | 不可变场景文档，schemaVersion=1 |
| `SimulationFactStore` | 不可变五端口事实存储，按 OrderId 分组索引 |

场景文件格式（JSON）：

```json
{
  "schemaVersion": 1,
  "observedAt": "2026-08-17T12:00:00Z",
  "orders": [...],
  "paymentTransactions": [...],
  "messageDeliveries": [...],
  "compensationTasks": [...],
  "traceSummaries": [...],
  "failures": []
}
```

`failures` 条目仅包含 `source`、`orderId` 和 `kind`（`UNAVAILABLE` 或 `TIMEOUT`），确保故障行为确定。

### 4.5 接口层（agent-api）

| 端点 | 说明 |
|---|---|
| `GET /api/status` | 状态检查（所有 profile） |
| `GET /api/orders/{orderId}` | 订单查询（仅 simulation profile） |
| `GET /api/diagnoses/orders/{orderId}` | 诊断查询（仅 simulation profile） |

错误响应使用稳定错误码，不暴露堆栈、SQL 或文件系统路径：

| 错误码 | HTTP 状态 | 触发条件 |
|---|---|---|
| `INVALID_ORDER_ID` | 400 | 订单号不合法 |
| `ORDER_NOT_FOUND` | 404 | 订单不存在 |
| `FACT_SOURCE_UNAVAILABLE` | 503 | 数据源不可用 |
| `FACT_SOURCE_TIMEOUT` | 503 | 数据源超时 |

## 5. 模拟场景

15 个场景覆盖：

| 场景 ID | 预期规则 |
|---|---|
| `SIM-NORMAL-001` | `NO_KNOWN_EXCEPTION` |
| `SIM-PAY-NOT-STARTED-001` | `PAYMENT_NOT_STARTED` |
| `SIM-PAY-TIMEOUT-001` | `PAYMENT_PROCESSING_TIMEOUT` |
| `SIM-CALLBACK-MISSING-001` | `PROVIDER_SUCCEEDED_CALLBACK_MISSING` |
| `SIM-ORDER-NOT-UPDATED-001` | `CALLBACK_SUCCEEDED_ORDER_NOT_UPDATED` |
| `SIM-PROVIDER-FAILED-001` | `PAYMENT_FAILED_WITH_PROVIDER_ERROR` |
| `SIM-MESSAGE-NOT-SENT-001` | `MESSAGE_NOT_SENT` |
| `SIM-MESSAGE-SEND-FAILED-001` | `MESSAGE_SEND_FAILED` |
| `SIM-MESSAGE-NOT-CONSUMED-001` | `MESSAGE_NOT_CONSUMED` |
| `SIM-MESSAGE-CONSUME-FAILED-001` | `MESSAGE_CONSUME_FAILED` |
| `SIM-COMP-NOT-CREATED-001` | `COMPENSATION_NOT_CREATED` |
| `SIM-COMP-FAILED-001` | `COMPENSATION_FAILED` |
| `SIM-COMP-EXHAUSTED-001` | `COMPENSATION_RETRIES_EXHAUSTED` |
| `SIM-TRACE-MISSING-INSUFFICIENT-001` | `TRACE_MISSING` / `INSUFFICIENT_EVIDENCE` |

正常链路不被强行归类为异常。

## 6. 部署资产

### 6.1 Flyway 迁移

`V2__create_diagnostic_fact_tables.sql` 创建五张表：

- `orders` — 脱敏订单快照
- `payment_transactions` — 支付流水
- `message_deliveries` — 消息投递
- `compensation_tasks` — 补偿任务
- `trace_summaries` — 调用链路摘要

包含主键、外键（ON DELETE RESTRICT）、CHECK 约束（与 Java 枚举一致）和按订单号查询的索引。

### 6.2 演示数据 SQL

`deploy/postgres/demo/001_payment_diagnosis_scenarios.sql`：

- 与 JSON 场景使用相同业务 ID 和事实语义；
- 使用 `INSERT ... ON CONFLICT DO UPDATE` 实现幂等；
- 不包含真实敏感数据。

### 6.3 消息事件契约

| 文件 | 说明 |
|---|---|
| `deploy/messaging/payment-events.schema.json` | JSON Schema draft 2020-12，定义事件信封 |
| `deploy/messaging/topology.json` | 厂商无关逻辑拓扑 |

三个逻辑事件：`payment.confirmed`、`order.state-update-requested`、`order.state-updated`。

## 7. 验证策略

### 7.1 领域与应用

- 每条规则至少一个命中用例；
- 关键规则有相邻但不应命中的反例；
- 验证规则优先级；
- 验证每个结论引用实际收集到的证据；
- 验证事实缺失、端口失败和端口超时的区别；
- 验证重复执行结果完全一致。

### 7.2 模拟适配器

- 加载完整场景；
- 拒绝未知 schema 版本、重复 ID、无效枚举值、负金额和断裂时间戳；
- 加载失败时包含逻辑 ID，不暴露文件系统路径。

### 7.3 API 契约

- 验证安全字段不出现（customerName、phone、address、token、secret 等）；
- 验证 ISO-8601 时间格式；
- 验证金额为两位小数 JSON 数值；
- 验证 dataMode=SIMULATION；
- 验证 400/404/503 错误响应；
- 验证默认 profile 下诊断端点返回 404。

### 7.4 部署资产

- Flyway SQL 包含五表、外键、索引和 CHECK 约束；
- 演示 SQL 与 JSON 场景 ID 一致；
- 消息 JSON 资产可被 Jackson 解析且包含必需字段。

## 8. 待完成事项

- 真实 PostgreSQL + Flyway 迁移执行验证；
- Testcontainers 仓储测试；
- 真实消息中间件拓扑初始化；
- 前端订单和诊断页面；
- 浏览器端验证；
- MCP 业务工具；
- LangChain4j Agent 工作流；
- RAG 知识检索；
- 评测数据集。
