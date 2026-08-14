# Payment Exception Diagnostics Agent

支付异常诊断 Agent：面向订单、支付流水、消息、补偿任务和调用链路的证据收集与异常阶段判断系统。

项目当前处于**工程骨架阶段**。后端、MCP Server 和前端均可构建、启动并完成状态验证；支付诊断工作流、MCP 查询工具和 RAG 尚未实现。项目不会使用固定文本或模拟模型回答伪装诊断能力。

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
| Agent API | 已完成骨架 | 提供状态接口和模型配置边界 |
| Java MCP Server | 已完成骨架 | Streamable HTTP `/mcp`，当前未注册业务工具 |
| Vue 3 前端 | 已完成骨架 | 展示 API 连通状态，未提供诊断界面 |
| PostgreSQL + pgvector | 已配置 | 提供 Docker Compose 和 vector 初始化 SQL |
| 支付诊断领域模型 | 部分完成 | 已有诊断阶段和诊断证据基础模型 |
| 订单/支付查询 | 未实现 | 后续按纵向切片开发 |
| Agent 工具调用 | 未实现 | 不提供假工具或假结果 |
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
│   ├── agent-domain/
│   ├── agent-application/
│   ├── agent-infrastructure/
│   ├── agent-api/
│   └── mcp-server/
├── frontend/
├── deploy/
│   ├── docker-compose.yml
│   └── postgres/init/001_pgvector.sql
└── docs/
    ├── design/project-design.md
    ├── roadmap/development-roadmap.md
    └── superpowers/
```

## 模块说明

| 模块 | 职责 |
|---|---|
| `agent-domain` | 领域模型、值对象、领域规则；不依赖 Spring 或基础设施 |
| `agent-application` | 诊断用例、工作流、端口接口和状态转换 |
| `agent-infrastructure` | PostgreSQL、pgvector、LangChain4j 和外部系统适配器 |
| `agent-api` | REST/SSE、参数校验、错误响应和应用装配 |
| `mcp-server` | 独立本地 MCP Server，后续暴露只读、强类型工具 |
| `frontend` | 诊断控制台、执行轨迹、证据和人工确认界面 |
| `deploy` | 本地 PostgreSQL + pgvector 编排和初始化 |

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

### 2. Agent API

先安装本地多模块依赖：

```bash
mvn install -DskipTests
```

启动 API：

```bash
mvn -f backend/agent-api/pom.xml spring-boot:run
```

默认端口：`8080`。

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

Agent API：

```bash
curl http://localhost:8080/api/status
```

```json
{"service":"payment-diagnostics-agent-api","state":"UP"}
```

MCP Server：

```bash
curl http://localhost:8081/api/status
```

```json
{"service":"payment-diagnostics-mcp-server","state":"UP","endpoint":"/mcp"}
```

MCP `initialize` 示例：

```bash
curl -X POST http://localhost:8081/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  --data '{
    "jsonrpc":"2.0",
    "id":1,
    "method":"initialize",
    "params":{
      "protocolVersion":"2025-11-25",
      "capabilities":{},
      "clientInfo":{"name":"local-smoke","version":"1.0.0"}
    }
  }'
```

## 安全原则

- MCP 查询工具默认只读、白名单、强类型。
- 禁止提供任意 SQL、Shell、文件读取或任意 URL 工具。
- 模型不能直接生成并执行 SQL。
- 敏感支付数据进入模型上下文前必须脱敏。
- 写操作和支付补偿必须由人工确认。
- 每次模型调用和工具调用必须可审计。
- 信息不足时返回“证据不足”，不能编造异常原因。
- Agent 必须设置步骤、超时、重试和成本上限。

## 文档

- [项目设计](docs/design/project-design.md)
- [开发路线](docs/roadmap/development-roadmap.md)
- [编码代理规则](AGENTS.md)
- [初始架构规格](docs/superpowers/specs/2026-08-14-payment-diagnostics-agent-design.md)
- [脚手架实施计划](docs/superpowers/plans/2026-08-14-project-scaffold.md)

## 当前验证结果

脚手架建立时已验证：

- Maven 全模块测试通过：6 个测试，0 失败；
- Maven 打包成功；
- Vue TypeScript 生产构建成功；
- Agent API 与 MCP Server 可独立启动；
- MCP `/mcp` 完成真实协议初始化；
- Chromium 中前端成功展示 API `UP` 状态。

由于验证环境未安装 Docker，PostgreSQL/pgvector 容器尚未实际启动验证。数据库相关功能进入首个数据切片前必须补做 Compose、迁移和连接验证。
