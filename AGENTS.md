# PaymentExceptionDiagnosticsAgent 编码代理指南

本文件是后续编码代理的项目级执行约束。任何自动化代理在修改仓库前必须先阅读本文件，并遵守“真实、可验证、最小权限”的原则。

## 项目边界

- 运行时：Java 21；Spring Boot 3.5.16；LangChain4j 1.18.1；MCP Java SDK 2.0.0；Vue 3 + TypeScript + Vite；PostgreSQL + pgvector。
- `backend/` 目录使用 Maven reactor 管理五个 Java 模块。
- Java 包根固定为 `com.leecardo.paymentdiagnostics`。
- 端口约定：API 服务 `8080`；MCP 服务 `8081`；前端 Vite `5173`。
- 状态响应字段固定为 `service`、`state`；MCP 状态额外包含 `endpoint=/mcp`。

## 模块职责

### `backend/agent-domain`

- 放置领域模型、值对象、领域服务接口、领域异常和不可变业务规则。
- 不依赖 Spring、LangChain4j、MCP SDK、数据库驱动或 Web 框架。
- 不执行外部 I/O，不读取环境变量，不拼接 SQL。

### `backend/agent-application`

- 编排用例：诊断请求接收后的流程、状态转换、端口接口、应用服务。
- 可以依赖 `agent-domain`，只通过端口接口描述基础设施能力。
- 不直接依赖 PostgreSQL、pgvector、HTTP client、MCP SDK 的具体实现。

### `backend/agent-infrastructure`

- 实现 application 定义的端口：数据库、pgvector/RAG 存取、LLM/embedding 客户端、外部支付系统连接器、配置映射。
- 可以依赖 `agent-domain` 和 `agent-application`。
- 所有外部调用必须有显式超时、错误边界和可测试的失败路径。

### `backend/agent-api`

- Spring Boot HTTP API 入口，负责 REST DTO、校验、错误响应、健康/状态端点和 CORS 等 Web 边界。
- 可以依赖 `agent-application`，需要运行时装配时才依赖 `agent-infrastructure`。
- 不在 Controller 中写诊断逻辑；Controller 只做协议转换。

### `backend/mcp-server`

- MCP server 入口，暴露诊断相关 MCP tools/resources/prompts。
- 可以依赖 `agent-application`，需要运行时装配时才依赖 `agent-infrastructure`。
- MCP 工具必须是白名单、强类型、可审计接口；禁止提供任意 SQL、任意 shell、任意文件系统读取工具。

### `frontend/`

- Vue 3 + TypeScript + Vite 前端，仅调用公开 API/MCP 状态接口。
- 不保存真实密钥；浏览器端不得承载服务端密钥、数据库凭据或 LLM API key。

### `deploy/`

- 本地开发与部署辅助文件。当前包含 PostgreSQL + pgvector Docker Compose 和初始化 SQL。
- Compose 只用于本地/开发默认值；生产部署必须由环境注入真实 secret，不从仓库读取。

## 依赖方向

依赖只能向内或向稳定抽象流动：

```text
agent-domain <- agent-application <- agent-api
             <- agent-application <- mcp-server
             <- agent-application <- agent-infrastructure
```

规则：

- `agent-domain` 不依赖其他项目模块。
- `agent-application` 只依赖 `agent-domain`。
- `agent-infrastructure` 依赖 `agent-domain`、`agent-application`，实现 application 端口。
- `agent-api` 和 `mcp-server` 依赖 `agent-application`；仅在启动装配层连接 infrastructure。
- 禁止从 domain/application 反向引用 API DTO、MCP SDK 类型、JPA 实体或 Spring Web 类型。
- 新增跨模块能力时，先在 application 定义端口，再在 infrastructure 实现，再由 API/MCP 调用应用服务。

## 常用命令

从仓库根目录执行：

```bash
# 构建所有 Java 模块
cd backend && mvn verify

# 启动 API 服务
cd backend && mvn -pl agent-api -am spring-boot:run

# 启动 MCP 服务
cd backend && mvn -pl mcp-server -am spring-boot:run

# 前端开发服务
cd frontend && npm install && npm run dev

# 本地 PostgreSQL + pgvector
cd deploy && docker compose up -d postgres
```

执行要求：

- 修改 Java 合约后，至少运行受影响 Maven 模块的编译或测试命令。
- 修改前端交互后，必须启动或构建前端并验证实际页面行为。
- 修改 Docker/SQL 后，至少验证 Compose/SQL 语法；若环境不允许 Docker，说明未运行 Docker 的原因。
- 本次脚手架任务明确要求跳过 docker、格式化和测试命令；后续任务不得把本例作为免验证先例。

## 安全约束

- 仓库禁止提交真实密钥、token、私钥、证书、数据库密码或供应商 API key。
- `.env.example` 只能包含本地默认值和占位符；真实 `.env` 必须被 `.gitignore` 忽略。
- 禁止新增任意 SQL 执行工具、任意 shell 工具、任意文件读取工具、任意 URL 抓取工具。
- 允许的 MCP 工具必须：名称固定、输入 schema 明确、权限最小、返回结构稳定、错误可审计。
- 所有外部系统调用必须设置超时，禁止无限等待。
- 日志不得打印密钥、完整支付凭据、银行卡号、身份证号、手机号等敏感信息；需要时只打印脱敏摘要或关联 ID。
- 数据库迁移必须可重复、可回滚或幂等；禁止依赖开发机本地隐式状态。
- LLM 输出不能作为事实来源直接落库或返回给用户；必须带来源、置信度或明确标识为模型推断。

## Agent / MCP / RAG 真实性门槛

禁止“看起来能跑”的假诊断实现。任何诊断、RAG 或 Agent 能力必须满足以下门槛：

- 诊断结论必须由输入事实、可追溯工具结果、RAG 引用或显式规则支撑。
- 没有证据时返回“证据不足/无法诊断”，不得编造支付异常原因。
- RAG 检索结果必须包含来源标识、片段内容或摘要、相似度/排序信息；不得只返回模型总结。
- 向量维度必须与 embedding 模型一致；当前初始化 SQL 默认 `vector(1536)`，更换模型时必须同步迁移。
- MCP tool 返回值必须区分：成功、输入错误、上游失败、证据不足。
- Agent 规划步骤不得调用不存在的工具；工具白名单必须由代码显式注册。
- 任何自动重试必须有次数上限，并保留最后失败原因。
- 测试不得 mock 掉被声明为真实门槛的核心行为后仍声称覆盖诊断能力；可以 mock 外部供应商，但必须验证应用层如何处理真实形状的响应。

## 验证要求

完成工作前必须提供与变更匹配的证据：

- 配置/构建变更：运行受影响的解析、编译或语法检查命令。
- API 合约变更：验证请求/响应字段和状态码，尤其是 `service`、`state`。
- MCP 合约变更：验证 MCP 状态包含 `service`、`state`、`endpoint=/mcp`，并验证 tool schema。
- RAG/数据库变更：验证迁移可执行、extension/index/table 名称正确、向量维度匹配。
- 前端变更：用真实浏览器或 dev server 验证页面，不只依赖类型检查。
- 安全相关变更：检查没有真实密钥、任意 SQL、任意 shell 或过宽文件访问能力。

如果环境无法运行某项验证，必须说明阻塞原因，并至少完成可运行的静态/语法级替代验证。
