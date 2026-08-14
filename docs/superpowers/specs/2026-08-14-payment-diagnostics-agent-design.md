# 支付异常诊断 Agent 架构设计

## 1. 目标

构建一个可演示、可评测的支付异常诊断 Agent。用户输入订单号或支付流水号后，系统通过受控只读工具收集订单、支付、消息、补偿任务和链路信息，给出带证据的异常阶段判断与处置建议。

本轮只建立可编译、可启动的工程骨架，不伪造诊断结论，不以静态假数据冒充已完成的 Agent 能力。

## 2. 技术基线

- Java 21
- Spring Boot 3
- Maven 多模块
- LangChain4j
- PostgreSQL + pgvector
- Vue 3 + TypeScript + Vite
- Java 本地 MCP Server，独立进程
- OpenAI 兼容模型接口，通过环境变量配置
- Docker Compose 本地依赖编排

具体依赖版本在搭建脚手架时选用彼此兼容的稳定版本，并由 Maven 和 npm 构建验证。

## 3. 代码结构

```text
PaymentExceptionDiagnosticsAgent/
├── pom.xml
├── AGENTS.md
├── backend/
│   ├── pom.xml
│   ├── agent-domain/
│   ├── agent-application/
│   ├── agent-infrastructure/
│   ├── agent-api/
│   └── mcp-server/
├── frontend/
├── deploy/
│   └── docker-compose.yml
└── docs/
    ├── superpowers/specs/
    └── plans/
```

## 4. 模块职责

### agent-domain

纯 Java 领域模型。包含订单、支付流水、消息投递、补偿任务、诊断证据、异常阶段和诊断结果等概念。不得依赖 Spring、LangChain4j、数据库或传输协议。

### agent-application

承载诊断用例和工作流。定义查询订单、支付、消息、补偿和 Trace 的端口；定义模型推理、审计、会话状态和人工确认端口。只依赖 `agent-domain`。

### agent-infrastructure

实现 PostgreSQL、pgvector、LangChain4j 模型客户端、知识检索和审计等端口。不得承载 HTTP 控制器或领域决策。

### agent-api

Spring Boot 主应用。提供 REST、SSE、健康检查和配置装配。初始骨架只提供明确的运行状态接口；诊断端点在实现首个纵向切片时加入。

### mcp-server

独立 Spring Boot 应用，以本地 MCP Server 方式暴露只读工具。后续工具包括订单、支付流水、消息、补偿任务和 Trace 查询。工具必须使用明确参数 Schema，不允许模型生成并直接执行任意 SQL。

### frontend

Vue 3 + TypeScript + Vite 应用。初始骨架展示服务状态；后续实现诊断输入、SSE 输出、证据列表、工具调用过程和人工确认界面。

## 5. 运行边界

- `agent-api` 与 `mcp-server` 为两个独立 Java 进程。
- PostgreSQL 使用带 pgvector 扩展的镜像。
- 前端只调用 `agent-api`，不直接访问数据库或 MCP Server。
- `agent-api` 通过 MCP 客户端接入本地 MCP Server；在该功能实现前，不添加假客户端或假诊断响应。
- 模型地址、模型名和密钥全部由环境变量提供。

## 6. 配置约定

建议环境变量：

- `OPENAI_BASE_URL`
- `OPENAI_API_KEY`
- `OPENAI_MODEL_NAME`
- `POSTGRES_DB`
- `POSTGRES_USER`
- `POSTGRES_PASSWORD`
- `SPRING_DATASOURCE_URL`
- `MCP_SERVER_URL` 或本地进程配置

仓库只提供 `.env.example`，不得提交真实密钥。

## 7. 数据设计原则

PostgreSQL 同时承载演示业务数据、审计数据和后续知识向量。业务表、Agent 运行记录、工具调用审计和向量文档应分开建模。

首批迁移只负责：

- 启用 `vector` 扩展；
- 建立数据库可用性验证所需的最小迁移机制；
- 不预先创建尚无用例支撑的大量业务表。

后续按纵向切片逐步增加订单、支付流水、消息、补偿任务、Agent Run、Tool Invocation 和 Knowledge Chunk。

## 8. 安全与可靠性约束

- 所有查询工具默认只读。
- 写操作和补偿操作必须经过人工确认。
- 工具输入执行 Schema 校验和白名单校验。
- 不允许任意 SQL 工具。
- 敏感字段进入模型上下文前必须脱敏。
- 所有模型和工具调用保存审计记录。
- Agent 设置最大步骤、超时和成本限制。
- 上游信息不足时返回未完成或证据不足，不编造结论。
- Prompt Injection 内容不得改变系统权限和工具边界。

## 9. 脚手架验收标准

- 根 Maven 构建能够编译所有 Java 模块。
- `agent-api` 能独立启动并通过健康检查。
- `mcp-server` 能独立启动并通过健康检查或协议级初始化检查。
- Vue 应用能够安装依赖、构建并启动。
- Docker Compose 能启动 PostgreSQL + pgvector。
- 数据库迁移能启用 vector 扩展。
- Java 应用缺少模型密钥时仍可启动基础健康检查，但任何模型调用必须明确失败，不得使用假回答。
- `AGENTS.md` 说明模块边界、命令、测试、安全和提交规则。
- 开发计划将业务实现拆成可验证纵向切片。

## 10. 非目标

本轮不实现：

- 完整支付诊断工作流；
- 真实支付平台或公司内部系统接入；
- 自动执行支付补偿；
- 多 Agent 编排；
- Kubernetes、云部署或 CI/CD；
- 生产级身份系统；
- 用 Mock 或固定文本伪装模型诊断能力。

## 11. 后续纵向切片顺序

1. 订单只读查询与前端展示；
2. 支付流水与订单状态联合判断；
3. 消息和补偿任务证据收集；
4. Java MCP Server 工具化上述查询；
5. LangChain4j 工具选择和证据汇总；
6. SSE 展示 Agent 执行轨迹；
7. 人工确认的补偿建议流程；
8. pgvector 知识检索与原文引用；
9. 固定数据集评测和调用可观测性。
