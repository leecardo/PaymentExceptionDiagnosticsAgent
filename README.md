# Payment Exception Diagnostics Agent

支付异常诊断 Agent：面向订单、支付流水、消息、补偿任务和调用链路的证据收集与异常阶段判断系统。

项目已完成**确定性诊断模拟后端切片**。通过 `simulation` profile，系统可从版本化场景数据加载事实，运行 15 条确定性诊断规则，并通过 REST API 返回带证据来源的诊断结论。真实 PostgreSQL、消息中间件和前端诊断界面尚未接入。

## 项目目标

用户输入订单号或支付流水号后，系统按受控流程：

1. 查询订单状态；
2. 查询支付流水；
3. 查询消息投递与消费状态；
4. 查询补偿任务状态；
5. 查询调用链路或监控证据；
6. 汇总证据并判断异常阶段；
7. 输出有来源的诊断结论和处置建议；
8. 对写操作或补偿操作要求人工确认。

## 当前状态

| 能力 | 状态 | 说明 |
|---|---|---|
| Maven 多模块工程 | 已完成 | Java 21，五个后端模块 |
| 领域模型 | 已完成 | 订单、支付、消息、补偿、Trace 五类不可变事实，15 条确定性诊断规则 |
| 应用层 | 已完成 | 五类只读查询端口、订单查询用例、诊断用例、固定顺序证据收集 |
| 基础设施模拟适配器 | 已完成 | 版本化 JSON 场景加载器、不可变五端口内存适配器、15 个模拟场景 |
| Agent API | 已完成 | 订单查询、诊断查询、稳定错误响应（400/404/503） |
| Java MCP Server | 已完成骨架 | Streamable HTTP `/mcp`，当前未注册业务工具 |
| Vue 3 前端 | 已完成骨架 | 展示 API 连通状态，未提供诊断界面 |
| PostgreSQL + pgvector | 已配置 | Flyway V2 建表迁移、脱敏演示数据 SQL 已就绪，未实际执行验证 |
| 消息事件契约 | 已完成 | 厂商无关事件 Schema 和拓扑描述，未绑定具体消息中间件 |
| Agent 工具调用 | 未实现 | 后续将诊断用例暴露为 MCP 白名单工具 |
| RAG 与评测 | 未实现 | 后续建立知识检索与固定评测集 |

## 技术栈

### 后端

- Java 21
- Spring Boot 3.5.16
- Maven 多模块
- LangChain4j 1.18.1
- MCP Java SDK 2.0.0
- Flyway
- PostgreSQL + pgvector

### 前端

- Vue 3
- TypeScript
- Vite 8

### 本地运行

- Docker Compose
- OpenAI 兼容模型接口

## 工程结构

```text
.
├── AGENTS.md
├── README.md
├── pom.xml
├── backend/
│   ├── pom.xml
│   ├── agent-domain/          # 领域模型、值对象、领域不变量
│   ├── agent-application/     # 诊断用例、查询端口、规则引擎
│   ├── agent-infrastructure/  # 模拟适配器、AI 模型配置
│   ├── agent-api/             # REST 控制器、错误处理、Spring 装配
│   └── mcp-server/            # 独立 MCP Server
├── frontend/
├── deploy/
│   ├── docker-compose.yml
│   ├── postgres/
│   │   ├── init/001_pgvector.sql
│   │   └── demo/001_payment_diagnosis_scenarios.sql
│   └── messaging/
│       ├── payment-events.schema.json
│       └── topology.json
└── docs/
    ├── design/project-design.md
    ├── design/simulation-flow.md
    ├── roadmap/development-roadmap.md
    └── superpowers/
```

## 模块说明

| 模块 | 职责 |
|---|---|
| `agent-domain` | 领域模型、值对象、领域规则；不依赖 Spring 或基础设施 |
| `agent-application` | 诊断用例、工作流、端口接口和状态转换 |
| `agent-infrastructure` | 模拟适配器、PostgreSQL/pgvector（待实现）、LangChain4j 和外部系统适配 |
| `agent-api` | REST/SSE、参数校验、错误响应和应用装配 |
| `mcp-server` | 独立本地 MCP Server，后续暴露只读、强类型工具 |
| `frontend` | 诊断控制台、执行轨迹、证据和人工确认界面 |
| `deploy` | PostgreSQL + pgvector 编排、演示数据和消息契约 |

依赖只允许向稳定内层流动：

```text
agent-domain <- agent-application <- agent-api
             <- agent-application <- mcp-server
             <- agent-application <- agent-infrastructure
```

## 环境要求

- JDK 21
- Maven 3.9+
- Node.js 20.19+ 或 22.12+
- npm
- Docker + Docker Compose（运行 PostgreSQL/pgvector 时需要）

## 配置

复制环境变量示例：

```bash
cp .env.example .env
```

主要配置：

| 变量 | 用途 |
|---|---|
| `OPENAI_BASE_URL` | OpenAI 兼容接口地址 |
| `OPENAI_API_KEY` | 模型接口密钥 |
| `OPENAI_MODEL_NAME` | 模型名称 |
| `APP_AI_ENABLED` | 是否启用 LangChain4j 模型 Bean |
| `POSTGRES_DB` | 数据库名 |
| `POSTGRES_USER` | 数据库用户 |
| `POSTGRES_PASSWORD` | 数据库密码 |
| `SPRING_DATASOURCE_URL` | JDBC 地址 |
| `APP_FLYWAY_ENABLED` | 是否运行 Flyway |
| `VITE_API_BASE_URL` | 前端 API 地址；开发代理模式可留空 |

真实密钥只能保存在未跟踪的本地 `.env` 或密钥管理系统中。

## 构建

### Java

```bash
mvn test
mvn package
```

### 前端

```bash
npm --prefix frontend install
npm --prefix frontend run build
```

## 本地启动

### 1. PostgreSQL + pgvector

```bash
docker compose -f deploy/docker-compose.yml up -d postgres
```

### 2. Agent API（模拟模式）

先安装本地多模块依赖：

```bash
mvn install -DskipTests
```

启动模拟模式 API：

```bash
mvn -f backend/agent-api/pom.xml spring-boot:run -Dspring-boot.run.profiles=simulation
```

默认端口：`8080`。`simulation` profile 激活后可访问订单和诊断接口。

### 3. MCP Server

```bash
mvn -f backend/mcp-server/pom.xml spring-boot:run
```

默认端口：`8081`；MCP 端点：`http://localhost:8081/mcp`。

### 4. 前端

```bash
npm --prefix frontend run dev
```

默认地址：`http://localhost:5173`。

## 当前可验证接口

### 状态接口

```bash
curl http://localhost:8080/api/status
```

```json
{"service":"payment-diagnostics-agent-api","state":"UP"}
```

### 订单查询（仅 simulation profile）

```bash
curl http://localhost:8080/api/orders/SIM-NORMAL-001
```

返回脱敏订单事实，不包含客户身份或配送地址等敏感字段。

### 诊断查询（仅 simulation profile）

```bash
curl http://localhost:8080/api/diagnoses/orders/SIM-CALLBACK-MISSING-001
```

返回诊断结果，包含 `dataMode=SIMULATION`、`ruleId`、`stage`、`evidence[]` 和 `warnings[]`。

### MCP Server

```bash
curl http://localhost:8081/api/status
```

```json
{"service":"payment-diagnostics-mcp-server","state":"UP","endpoint":"/mcp"}
```

### 错误响应

| 场景 | HTTP 状态 | 错误码 |
|---|---|---|
| 订单号非法 | 400 | `INVALID_ORDER_ID` |
| 订单不存在 | 404 | `ORDER_NOT_FOUND` |
| 数据源不可用 | 503 | `FACT_SOURCE_UNAVAILABLE` |
| 数据源超时 | 503 | `FACT_SOURCE_TIMEOUT` |

## 安全原则

- MCP 查询工具默认只读、白名单、强类型。
- 禁止提供任意 SQL、Shell、文件读取或任意 URL 工具。
- 模型不能直接生成并执行 SQL。
- 敏感支付数据进入模型上下文前必须脱敏。
- 写操作和支付补偿必须由人工确认。
- 每次模型调用和工具调用必须可审计。
- 信息不足时返回"证据不足"，不能编造异常原因。
- Agent 必须设置步骤、超时、重试和成本上限。

## 文档

- [项目设计](docs/design/project-design.md)
- [模拟流程设计](docs/design/simulation-flow.md)
- [开发路线](docs/roadmap/development-roadmap.md)
- [编码代理规则](AGENTS.md)
- [初始架构规格](docs/superpowers/specs/2026-08-14-payment-diagnostics-agent-design.md)
- [脚手架实施计划](docs/superpowers/plans/2026-08-14-project-scaffold.md)

## 当前验证结果

确定性诊断模拟后端切片验证：

- Maven 全模块测试通过：148 个测试，0 失败；
  - 领域层 48 | 应用层 50 | 基础设施层 28 | API 层 21 | MCP 1
- API 烟雾流程已执行：
  - `simulation` profile 下订单查询和诊断查询返回正确结果；
  - 非法订单号返回 400，不存在订单返回 404；
  - 默认 profile 下诊断端点返回 404（模拟端点正确隔离）；
- 部署资产已验证：
  - Flyway V2 迁移包含五表、约束、索引；
  - 脱敏演示数据 SQL 与 JSON 场景一致；
  - 消息事件契约和拓扑描述结构正确。

由于验证环境未安装 Docker，PostgreSQL/pgvector 容器和 Flyway 迁移尚未在实际数据库上执行验证。
