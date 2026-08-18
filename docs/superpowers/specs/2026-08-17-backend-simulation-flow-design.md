# 支付异常诊断后端模拟流程设计

## 1. 目标

在本地暂不启动 PostgreSQL、消息队列等中间件的前提下，先完成可运行、可验证的后端支付异常模拟流程，并同时准备未来部署可使用的 PostgreSQL 初始化脚本和脱敏演示数据。

模拟流程必须基于结构化事实和确定性规则，不使用固定诊断文本冒充真实能力。未来接入 PostgreSQL 或具体消息中间件时，应用用例和领域规则不变，只替换基础设施适配器。

## 2. 本轮范围

本轮实现：

- 订单、支付流水、消息投递、补偿任务和 Trace 摘要领域事实；
- 五类只读查询端口；
- 从版本化场景数据文件加载事实的内存适配器；
- 固定顺序的证据收集流程；
- 第一批确定性诊断规则；
- 后端订单查询和诊断查询 API；
- PostgreSQL Flyway 建表、约束和索引脚本；
- 独立、可选的 PostgreSQL 脱敏演示数据脚本；
- 厂商无关的消息事件契约和拓扑描述文件；
- 领域、应用、适配器和 API 契约测试。

本轮不实现：

- 启动或连接 PostgreSQL、pgvector、Redis、RabbitMQ、RocketMQ、Kafka；
- 真实消息发布或消费；
- 厂商特定且不可验证的 MQ 初始化命令；
- 前端订单或诊断页面；
- MCP 业务工具；
- LangChain4j Agent、模型推理或 RAG；
- 自动补偿执行；
- 随机故障注入。

## 3. 核心决策

### 3.1 端口不变，适配器切换

领域层和应用层只认识查询端口。运行方式由 Spring profile 决定：

- `simulation`：从仓库内的版本化场景文件加载内存适配器；
- 后续 `postgres`：通过参数化 SQL 查询 PostgreSQL；
- 默认 profile 不启用模拟诊断能力，避免把模拟结果误认为真实系统事实。

API、固定工作流和规则诊断器不得判断当前适配器类型，也不得包含模拟场景分支。

### 3.2 模拟事实不等于 Mock 结论

场景文件只声明可观察事实，例如订单状态、支付流水、回调时间、消息发送和消费状态、补偿重试次数、Trace 摘要。诊断结果必须由规则诊断器运行后产生。

API 响应必须标识数据模式为 `SIMULATION`，证据来源必须能定位到具体场景事实。不得把预先写好的“异常原因”字段直接作为诊断结论。

### 3.3 暂不绑定消息中间件

参考系统同时存在 RabbitMQ、RocketMQ 和 Redis 用法，本项目当前没有明确 MQ 选型。因此本轮：

- 使用 `message_deliveries` 事实模型表达消息是否创建、发送、消费和失败；
- 使用厂商无关的事件契约描述事件名、版本、关联键和负载字段；
- 使用厂商无关的拓扑描述表达逻辑 channel、生产者、消费者和失败处理意图；
- 不提交声称可直接执行的 RabbitMQ、RocketMQ 或 Kafka 初始化脚本。

确定具体 MQ 后，再从事件契约生成或编写真实的 exchange/queue/binding、topic/group 等初始化资源。

## 4. 分层设计

### 4.1 `agent-domain`

新增不可变领域类型：

- `OrderId`
- `OrderStatus`
- `OrderRole`
- `OrderSnapshot`
- `PaymentTransaction`
- `PaymentStatus`
- `MessageDelivery`
- `MessageDeliveryStatus`
- `CompensationTask`
- `CompensationStatus`
- `TraceSummary`
- `DiagnosisRuleId`
- `DiagnosisResult`

领域不变量包括：

- 标识符非空、去除首尾空白且长度有上限；
- 金额使用 `BigDecimal`，不得为负；
- 商品数量大于零；
- 更新时间不得早于创建时间；
- 子订单必须有主订单号，单订单和主订单不得错误携带主订单号；
- 重试次数不得为负，且不得大于配置的最大重试次数；
- 每个确定性诊断结论至少引用一条 `DiagnosisEvidence`；
- 无足够事实时返回 `INSUFFICIENT_EVIDENCE`。

订单状态以参考 `/prodCharge/generateOrder` 实际写入的 `prod_order_user.ORDER_STATE` 为准：

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

不混用 `prod_order` 中包含退单阶段的另一套状态定义。

### 4.2 `agent-application`

定义只读端口：

- `OrderQueryPort`
- `PaymentQueryPort`
- `MessageQueryPort`
- `CompensationQueryPort`
- `TraceQueryPort`

定义用例：

- `GetOrderUseCase`
- `DiagnosePaymentExceptionUseCase`

固定诊断流程：

1. 校验订单号；
2. 查询订单；
3. 订单存在时查询关联支付流水；
4. 查询消息投递事实；
5. 查询补偿任务事实；
6. 查询 Trace 摘要；
7. 将每条事实转换为可追溯证据；
8. 按明确优先级运行确定性规则；
9. 返回阶段、规则编号、证据和数据模式。

查询失败必须与“没有业务记录”分开：

- 非法输入；
- 订单不存在；
- 查询成功但关联事实不存在；
- 数据源不可用；
- 数据源超时；
- 证据不足。

一个下游查询失败不得伪装成业务记录缺失。例如消息查询异常不能被诊断为“消息未发送”。

### 4.3 `agent-infrastructure`

新增 simulation 适配器：

- 仅在 `simulation` profile 激活；
- 启动时读取一个版本化场景文件；
- 加载失败时应用启动失败并指出场景和字段位置，不静默使用空数据；
- 场景数据加载后保持不可变；
- 同一输入始终产生同一事实和结论；
- 不使用随机数、当前时间或网络请求改变场景结果。

场景文件应采用项目现有 Jackson/Spring 可直接解析的格式，优先 JSON。顶层包含 schema 版本和场景列表；每个场景包含五类事实，但不包含预制诊断结论。

未来 PostgreSQL 适配器实现相同端口，使用固定、参数化查询，并设置显式查询超时。

### 4.4 `agent-api`

新增后端接口：

```http
GET /api/orders/{orderId}
GET /api/diagnoses/orders/{orderId}
```

订单查询响应只返回脱敏订单事实。

诊断响应至少包含：

- `orderId`
- `dataMode`
- `stage`
- `ruleId`
- `summary`
- `evidence[]`
- `warnings[]`

HTTP 状态：

- `200`：查询或诊断流程完成，包括 `INSUFFICIENT_EVIDENCE`；
- `400`：订单号非法；
- `404`：订单不存在；
- `503`：必要数据源不可用或超时。

异常响应使用稳定错误码，不暴露堆栈、SQL、文件系统绝对路径或敏感字段。

## 5. PostgreSQL 初始化资产

### 5.1 Flyway 迁移

在 `agent-api` 的 Flyway 目录按版本新增表结构迁移：

- `orders`
- `payment_transactions`
- `message_deliveries`
- `compensation_tasks`
- `trace_summaries`

迁移包含：

- 主键和外键；
- 金额、数量、时间和重试次数约束；
- 状态 `CHECK` 约束；
- 订单号、支付流水号、消息关联键和 Trace ID 索引；
- 支持按订单号完成固定工作流查询的必要索引；
- 幂等创建由 Flyway 版本管理保证，不使用会掩盖迁移错误的宽泛异常处理。

不把脱敏演示数据放进默认 Flyway 生产迁移，避免部署启动时自动灌入测试数据。

### 5.2 演示数据 SQL

在 `deploy/postgres/demo/` 提供显式执行的脱敏演示数据 SQL：

- 使用固定 ID，重复执行结果稳定；
- 使用 `INSERT ... ON CONFLICT ...` 或明确清理策略实现幂等；
- 不包含真实姓名、手机号、地址、令牌、密钥或供应商凭据；
- 与 simulation 场景使用相同业务 ID 和事实语义；
- 文件头说明依赖的 Flyway 版本和执行顺序。

本地无 PostgreSQL 时，只能验证脚本结构、对象引用和场景一致性；不得声称已在真实 PostgreSQL 执行成功。

## 6. 消息事件契约

在部署资产中提供厂商无关契约，至少定义：

- 逻辑事件：`payment.confirmed`、`order.state-update-requested`、`order.state-updated`；
- 事件版本；
- `eventId`、`orderId`、`correlationId`、`occurredAt`；
- 最小业务负载；
- 生产者和逻辑消费者；
- 幂等键；
- 预期重试和失败处理语义；
- 禁止进入消息的敏感字段。

契约文件用于模拟数据校验和未来 MQ 选型，不作为当前已部署消息能力的证明。

## 7. 首批确定性规则

按业务阶段和证据优先级实现：

1. `ORDER_NOT_FOUND`：订单不存在，由 API 返回 `404`，不形成虚构诊断；
2. `PAYMENT_NOT_STARTED`：订单待支付且没有支付流水；
3. `PAYMENT_PROCESSING_TIMEOUT`：支付仍处理中且超过明确阈值；
4. `PROVIDER_SUCCEEDED_CALLBACK_MISSING`：供应商成功但回调未接收；
5. `CALLBACK_SUCCEEDED_ORDER_NOT_UPDATED`：回调成功但订单仍未进入已支付或后续状态；
6. `PAYMENT_FAILED_WITH_PROVIDER_ERROR`：支付失败且存在明确渠道错误码；
7. `MESSAGE_NOT_SENT`：应发送的消息记录不存在；
8. `MESSAGE_SEND_FAILED`：消息发送失败；
9. `MESSAGE_NOT_CONSUMED`：已发送但在阈值内未消费；
10. `MESSAGE_CONSUME_FAILED`：消费失败；
11. `COMPENSATION_NOT_CREATED`：符合补偿条件但无补偿任务；
12. `COMPENSATION_FAILED`：补偿执行失败且尚可重试；
13. `COMPENSATION_RETRIES_EXHAUSTED`：重试次数达到上限；
14. `TRACE_MISSING`：其他事实不足且 Trace 缺失时作为证据缺口，不覆盖更明确的业务错误；
15. `INSUFFICIENT_EVIDENCE`：没有规则具备足够事实。

时间阈值必须来自显式应用配置或场景观察时间，测试中固定，不直接依赖系统当前时间。

## 8. 首批模拟场景

场景集合包含：

- 正常支付完整链路；
- 订单不存在；
- 未发起支付；
- 支付处理中超时；
- 三方成功但回调缺失；
- 回调成功但订单未更新；
- 渠道明确失败；
- 消息未发送；
- 消息发送失败；
- 消息未消费；
- 消息消费失败；
- 补偿任务未创建；
- 补偿失败但可重试；
- 补偿重试耗尽；
- Trace 缺失且证据不足。

正常链路不得被强行归类为异常；应返回没有发现已知异常，且证据仍可追溯。

## 9. 验证策略

### 9.1 领域与应用

- 每条规则至少一个命中用例；
- 关键规则具有相邻但不应命中的反例；
- 验证规则优先级，防止后阶段错误覆盖更早且更明确的异常；
- 验证每个结论引用实际收集到的证据；
- 验证事实缺失、端口失败和端口超时的区别；
- 验证重复执行结果完全一致。

### 9.2 simulation 适配器

- 加载完整场景；
- 拒绝未知 schema 版本；
- 拒绝重复业务 ID、非法状态、负金额和不一致关联；
- 查询不存在的数据返回空结果，而不是异常；
- 声明的数据源失败按场景确定性返回对应错误。

### 9.3 API

- 验证成功响应字段；
- 验证 `dataMode=SIMULATION`；
- 验证 `400`、`404` 和 `503`；
- 验证响应不包含姓名、手机号、地址、密钥或完整支付凭据。

### 9.4 SQL 和部署资产

无 PostgreSQL/MQ 环境时执行：

- Maven 全量测试和构建；
- 场景文件解析；
- SQL 表名、外键、索引和演示数据引用的一致性检查；
- 消息契约 schema 校验；
- simulation 与 SQL 演示事实 ID/状态的一致性测试。

真实 PostgreSQL 执行、Flyway 迁移和具体 MQ 初始化保留为后续部署验收，必须明确标记未执行。

## 10. 后续切换

模拟流程通过后，按以下顺序替换基础设施：

1. 在 PostgreSQL 环境执行 Flyway；
2. 显式导入演示数据 SQL；
3. 实现并启用 PostgreSQL 查询适配器；
4. 对同一业务场景运行端口契约测试，比较 simulation 和 PostgreSQL 结果；
5. 确定具体 MQ；
6. 基于事件契约增加厂商特定拓扑初始化资源；
7. 接入真实只读消息状态查询或审计表；
8. 禁用 simulation profile 后执行端到端验证。

应用用例、诊断规则和 API 契约在此切换中保持不变。

## 11. 完成标准

- 后端在 `simulation` profile 下无需外部中间件即可启动；
- 两个 API 能基于场景事实返回稳定结果；
- 每条诊断结论均由规则和证据产生；
- 首批场景和错误边界有行为测试；
- PostgreSQL 表结构和可选演示数据脚本已准备；
- 厂商无关消息契约和拓扑描述已准备，但不宣称具体 MQ 已部署；
- 默认运行模式不悄然启用模拟数据；
- 前端、MCP、模型和 RAG 保持不变；
- `mvn verify` 通过；
- 文档明确标识哪些行为已实际验证、哪些等待真实中间件环境验证。
