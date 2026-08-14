# Payment Exception Diagnostics Agent Scaffold Implementation Plan

> **For agentic workers:** Use `subagent-driven-development` when this plan has 2+ independent tasks that can be delegated cleanly. Use `executing-plans` when subagents are unavailable, intentionally disabled, or unsafe for tightly coupled work. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a Java 21/Spring Boot 3/LangChain4j/PostgreSQL-pgvector/Vue 3/TypeScript repository whose API, local Java MCP server, frontend, and database can be built and started independently.

**Architecture:** Use a Maven reactor with pure domain and application libraries, an infrastructure adapter library, and two Spring Boot processes: `agent-api` and `mcp-server`. Keep the frontend in a separate Vite workspace and run PostgreSQL/pgvector through Docker Compose. The scaffold exposes only health/status behavior; payment diagnosis enters later as tested vertical slices.

**Tech Stack:** Java 21, Spring Boot 3.5.16, LangChain4j 1.18.1, MCP Java SDK 2.0.0, PostgreSQL/pgvector, Flyway, Vue 3.5, TypeScript, Vite 8, Docker Compose.

---

## File map

- `pom.xml`: repository Maven reactor and dependency versions.
- `backend/pom.xml`: Java module aggregator.
- `backend/agent-domain`: dependency-free diagnosis domain vocabulary.
- `backend/agent-application`: use-case ports and orchestration boundary.
- `backend/agent-infrastructure`: LangChain4j and PostgreSQL adapters/configuration.
- `backend/agent-api`: primary HTTP application and Flyway migration owner.
- `backend/mcp-server`: independent local Streamable HTTP MCP process.
- `frontend`: Vue status surface and Vite proxy.
- `deploy/docker-compose.yml`: local pgvector database.
- `AGENTS.md`: repository-wide engineering rules and commands.

### Task 1: Repository governance and Maven reactor

**Files:**
- Create: `.gitignore`
- Create: `.env.example`
- Create: `AGENTS.md`
- Create: `pom.xml`
- Create: `backend/pom.xml`

- [ ] **Step 1: Define Java 21 and dependency versions**

Set Maven compiler release to 21 and manage Spring Boot 3.5.16, LangChain4j 1.18.1, and MCP SDK 2.0.0 from the root reactor.

- [ ] **Step 2: Register five backend modules**

Register `agent-domain`, `agent-application`, `agent-infrastructure`, `agent-api`, and `mcp-server` in `backend/pom.xml`.

- [ ] **Step 3: Add repository rules**

Document module dependency direction, no-arbitrary-SQL policy, secret handling, exact build/run commands, test expectations, and clean-cutover rules in `AGENTS.md`.

- [ ] **Step 4: Verify reactor discovery**

Run: `mvn -q validate`
Expected: exit 0 and every declared module resolves.

### Task 2: Domain and application boundaries

**Files:**
- Create: `backend/agent-domain/pom.xml`
- Create: `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/DiagnosisStage.java`
- Create: `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/DiagnosisEvidence.java`
- Create: `backend/agent-application/pom.xml`
- Create: `backend/agent-application/src/main/java/com/leecardo/paymentdiagnostics/application/package-info.java`
- Test: `backend/agent-domain/src/test/java/com/leecardo/paymentdiagnostics/domain/DiagnosisEvidenceTest.java`

- [ ] **Step 1: Test evidence invariants**

Test that blank source, blank summary, and missing observation time are rejected by `DiagnosisEvidence`.

- [ ] **Step 2: Implement minimal domain vocabulary**

Add an explicit diagnosis-stage enum and immutable evidence record with constructor validation. Do not add persistence or Spring annotations.

- [ ] **Step 3: Reserve the application boundary**

Create documented application package only; do not invent use cases before the first order-query slice.

- [ ] **Step 4: Run domain tests**

Run: `mvn -pl backend/agent-domain -am test`
Expected: tests pass.

### Task 3: Infrastructure and API application

**Files:**
- Create: `backend/agent-infrastructure/pom.xml`
- Create: `backend/agent-infrastructure/src/main/java/com/leecardo/paymentdiagnostics/infrastructure/ai/AiModelProperties.java`
- Create: `backend/agent-infrastructure/src/main/java/com/leecardo/paymentdiagnostics/infrastructure/ai/AiModelConfiguration.java`
- Create: `backend/agent-api/pom.xml`
- Create: `backend/agent-api/src/main/java/com/leecardo/paymentdiagnostics/api/AgentApiApplication.java`
- Create: `backend/agent-api/src/main/java/com/leecardo/paymentdiagnostics/api/status/ServiceStatusController.java`
- Create: `backend/agent-api/src/main/resources/application.yml`
- Create: `backend/agent-api/src/main/resources/db/migration/V1__enable_vector_extension.sql`
- Test: `backend/agent-api/src/test/java/com/leecardo/paymentdiagnostics/api/status/ServiceStatusControllerTest.java`

- [ ] **Step 1: Test the public status contract**

Use MockMvc to require `GET /api/status` to return service name `payment-diagnostics-agent-api` and state `UP`.

- [ ] **Step 2: Add opt-in LangChain4j configuration**

Bind `app.ai.base-url`, `api-key`, and `model-name`; create the OpenAI-compatible chat model only when `app.ai.enabled=true`. Missing credentials must fail configuration instead of creating a fake model.

- [ ] **Step 3: Add API application and status endpoint**

Create the Spring Boot entrypoint, status response, Actuator health exposure, datasource configuration, and Flyway migration enabling `vector`.

- [ ] **Step 4: Run API tests**

Run: `mvn -pl backend/agent-api -am test`
Expected: MockMvc contract passes without a real model or database.

### Task 4: Local Java MCP server

**Files:**
- Create: `backend/mcp-server/pom.xml`
- Create: `backend/mcp-server/src/main/java/com/leecardo/paymentdiagnostics/mcp/McpServerApplication.java`
- Create: `backend/mcp-server/src/main/java/com/leecardo/paymentdiagnostics/mcp/config/McpServerConfiguration.java`
- Create: `backend/mcp-server/src/main/java/com/leecardo/paymentdiagnostics/mcp/status/McpStatusController.java`
- Create: `backend/mcp-server/src/main/resources/application.yml`
- Test: `backend/mcp-server/src/test/java/com/leecardo/paymentdiagnostics/mcp/status/McpStatusControllerTest.java`

- [ ] **Step 1: Test MCP process status**

Use MockMvc to require `GET /api/status` to identify `payment-diagnostics-mcp-server` and advertise endpoint `/mcp`.

- [ ] **Step 2: Configure Streamable HTTP transport**

Register the official MCP Java SDK servlet transport at `/mcp`, create an empty synchronous server with tool capability enabled, and close it on shutdown. An empty tool list is honest scaffold behavior.

- [ ] **Step 3: Run MCP tests and protocol smoke test**

Run: `mvn -pl backend/mcp-server -am test`
Expected: tests pass.

After launch, send an MCP initialize request to `/mcp`; expected: a JSON-RPC response or protocol-defined session response, not HTTP 404.

### Task 5: Vue 3 TypeScript frontend

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/package-lock.json` via npm
- Create: `frontend/index.html`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.app.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/src/env.d.ts`
- Create: `frontend/src/main.ts`
- Create: `frontend/src/App.vue`
- Create: `frontend/src/style.css`

- [ ] **Step 1: Create typed status client surface**

Build a single Vue page that requests `/api/status`, renders loading/up/unavailable states, and states that diagnosis workflow is not yet implemented.

- [ ] **Step 2: Configure Vite proxy**

Proxy `/api` to `http://localhost:8080`; keep the backend URL configurable with `VITE_API_BASE_URL` for built deployments.

- [ ] **Step 3: Install and build**

Run: `npm install`
Expected: lockfile generated.

Run: `npm run build`
Expected: TypeScript and Vite build exit 0.

### Task 6: Local database and end-to-end scaffold verification

**Files:**
- Create: `deploy/docker-compose.yml`
- Create: `deploy/postgres/init/00-enable-vector.sql`

- [ ] **Step 1: Define pgvector service**

Use a pinned pgvector PostgreSQL image, persistent named volume, health check, and environment-variable credentials with safe local defaults.

- [ ] **Step 2: Validate Compose configuration**

Run: `docker compose -f deploy/docker-compose.yml config`
Expected: exit 0.

- [ ] **Step 3: Run full builds**

Run: `mvn test`
Expected: all Java modules compile and tests pass.

Run: `npm --prefix frontend run build`
Expected: frontend production build succeeds.

- [ ] **Step 4: Smoke-test actual services**

Start PostgreSQL, `agent-api`, `mcp-server`, and Vite. Request both status endpoints, initialize MCP over `/mcp`, and load the frontend in Chromium. Expected: both Java processes report UP, MCP endpoint responds at protocol level, and the page reports the API service status without console errors.

- [ ] **Step 5: Cleanup development processes**

Stop only processes started for the smoke test. Preserve source and generated lockfiles; do not commit secrets or build output.
