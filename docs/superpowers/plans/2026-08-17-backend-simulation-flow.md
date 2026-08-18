# Backend Simulation Flow Implementation Plan

> **For agentic workers:** Use `subagent-driven-development` when this plan has 2+ independent tasks that can be delegated cleanly. Use `executing-plans` when subagents are unavailable, intentionally disabled, or unsafe for tightly coupled work. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a middleware-free, profile-gated backend simulation that derives payment-exception diagnoses from versioned facts while preparing PostgreSQL and vendor-neutral message initialization assets.

**Architecture:** Domain records enforce immutable business invariants. Application ports and a fixed workflow collect five fact types and run ordered deterministic rules. A `simulation` Spring profile loads versioned JSON into an immutable adapter; REST controllers expose order and diagnosis contracts. PostgreSQL/MQ assets remain deployment inputs, not runtime dependencies in this iteration.

**Tech Stack:** Java 21, Spring Boot 3.5.16, Jackson, JUnit 5, MockMvc, Flyway SQL, PostgreSQL 17 syntax, JSON Schema.

---

## File map

### Domain

- Create `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/OrderId.java`: normalized, bounded order identifier.
- Create `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/OrderStatus.java`: `prod_order_user` status vocabulary.
- Create `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/OrderRole.java`: single/master/sub relationship.
- Create `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/OrderSnapshot.java`: safe order facts.
- Create `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/PaymentStatus.java` and `PaymentTransaction.java`: provider and callback facts.
- Create `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/MessageDeliveryStatus.java` and `MessageDelivery.java`: logical send/consume facts.
- Create `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/CompensationStatus.java` and `CompensationTask.java`: bounded retry facts.
- Create `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/TraceSummary.java`: trace availability facts.
- Create `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/DiagnosisRuleId.java`, `DataMode.java`, and `DiagnosisResult.java`: deterministic result contract.
- Modify `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/DiagnosisEvidence.java`: add a stable evidence ID without losing validation.
- Modify `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/DiagnosisStage.java`: add callback, order-update, and normal-completion stages required by rules.
- Create focused tests under `backend/agent-domain/src/test/java/com/leecardo/paymentdiagnostics/domain/`.

### Application

- Create `backend/agent-application/src/main/java/com/leecardo/paymentdiagnostics/application/port/` with five query ports and `FactQueryException`.
- Create `backend/agent-application/src/main/java/com/leecardo/paymentdiagnostics/application/order/GetOrderUseCase.java` and `OrderNotFoundException.java`.
- Create `backend/agent-application/src/main/java/com/leecardo/paymentdiagnostics/application/diagnosis/DiagnosisPolicy.java`, `DeterministicDiagnosisRules.java`, and `DiagnosePaymentExceptionUseCase.java`.
- Create application behavior tests under `backend/agent-application/src/test/java/com/leecardo/paymentdiagnostics/application/`.
- Modify `backend/agent-application/pom.xml`: add JUnit test dependency.

### Infrastructure

- Create `backend/agent-infrastructure/src/main/java/com/leecardo/paymentdiagnostics/infrastructure/simulation/SimulationConfiguration.java`.
- Create `SimulationScenarioDocument.java`, `SimulationScenarioLoader.java`, and `SimulationFactStore.java` in the same package.
- Create `backend/agent-infrastructure/src/main/resources/simulation/payment-diagnosis-scenarios.json`.
- Create loader/store tests and invalid fixtures under `backend/agent-infrastructure/src/test/`.
- Modify `backend/agent-infrastructure/pom.xml`: add Jackson, Spring context, and test dependencies required by the adapter.

### API and configuration

- Modify `backend/agent-api/src/main/java/com/leecardo/paymentdiagnostics/api/AgentApiApplication.java`: import simulation/application configuration.
- Create `backend/agent-api/src/main/java/com/leecardo/paymentdiagnostics/api/order/OrderController.java`.
- Create `backend/agent-api/src/main/java/com/leecardo/paymentdiagnostics/api/diagnosis/DiagnosisController.java`.
- Create `backend/agent-api/src/main/java/com/leecardo/paymentdiagnostics/api/error/ApiExceptionHandler.java` and `ApiError.java`.
- Create `backend/agent-api/src/main/java/com/leecardo/paymentdiagnostics/api/config/DiagnosticUseCaseConfiguration.java`.
- Modify `backend/agent-api/src/main/resources/application.yml`: add deterministic thresholds and simulation resource configuration without activating the profile.
- Create API contract tests under `backend/agent-api/src/test/java/com/leecardo/paymentdiagnostics/api/`.

### Deployment assets

- Create `backend/agent-api/src/main/resources/db/migration/V2__create_diagnostic_fact_tables.sql`.
- Create `deploy/postgres/demo/001_payment_diagnosis_scenarios.sql`.
- Create `deploy/messaging/payment-events.schema.json` and `deploy/messaging/topology.json`.
- Create `backend/agent-infrastructure/src/test/java/com/leecardo/paymentdiagnostics/infrastructure/simulation/DeploymentAssetConsistencyTest.java` to compare scenario IDs/statuses with SQL and validate message JSON assets.
- Modify `docs/roadmap/development-roadmap.md` only after verification: record simulation-path completion separately from real PostgreSQL/MQ verification.

### Important contract adjustment

`DiagnosisEvidence` currently has `(source, summary, observedAt)`. Replace it cleanly with `(id, source, summary, observedAt)` and migrate its existing test and every constructor. No compatibility constructor: the repository has only one current test caller, and clean cutover avoids unstable evidence references.

---

### Task 1: Establish immutable order facts

**Files:**
- Create: `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/OrderId.java`
- Create: `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/OrderStatus.java`
- Create: `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/OrderRole.java`
- Create: `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/OrderSnapshot.java`
- Test: `backend/agent-domain/src/test/java/com/leecardo/paymentdiagnostics/domain/OrderIdTest.java`
- Test: `backend/agent-domain/src/test/java/com/leecardo/paymentdiagnostics/domain/OrderSnapshotTest.java`

- [ ] **Step 1: Write failing identifier and order-invariant tests**

Test exact behavior: trim IDs, reject blank/over-64/illegal characters; reject negative amounts, non-positive goods count, `updatedAt < createdAt`, SUB without master ID, and SINGLE/MASTER with master ID. Use fixed `Instant` values.

```java
assertEquals("4101_order-001", new OrderId(" 4101_order-001 ").value());
assertThrows(IllegalArgumentException.class, () -> new OrderId("bad/id"));
assertThrows(IllegalArgumentException.class, () -> order(OrderRole.SUB, null));
```

- [ ] **Step 2: Run domain tests and verify red state**

Run: `mvn -pl backend/agent-domain -am test`

Expected: compilation fails because the new domain types do not exist.

- [ ] **Step 3: Implement the order types**

Use this public shape:

```java
public record OrderId(String value) {
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    public OrderId {
        Objects.requireNonNull(value, "value must not be null");
        value = value.strip();
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("orderId must contain 1-64 letters, digits, '.', '_' or '-'");
        }
    }
}

public enum OrderStatus {
    PENDING_PAYMENT, CANCELLED, PAID, OUTBOUND, SHIPPED, SIGNED, COMPLETED, CLOSED
}

public enum OrderRole { SINGLE, MASTER, SUB }

public record OrderSnapshot(
        OrderId orderId,
        OrderId masterOrderId,
        OrderRole role,
        String productId,
        String productName,
        String productType,
        int goodsCount,
        BigDecimal unitPrice,
        BigDecimal orderAmount,
        BigDecimal paymentAmount,
        String paymentSource,
        String providerOrderId,
        String orderSource,
        OrderStatus status,
        Instant orderedAt,
        Instant stateChangedAt,
        Instant createdAt,
        Instant updatedAt) { }
```

Normalize optional text to `null`, required text with `strip()`, and apply the listed invariants in the compact constructor. Do not add customer identity or delivery fields.

- [ ] **Step 4: Run domain tests and verify green state**

Run: `mvn -pl backend/agent-domain -am test`

Expected: all order domain tests pass.

### Task 2: Establish payment, message, compensation, and trace facts

**Files:**
- Create the eight fact/status files listed in the file map.
- Test: `backend/agent-domain/src/test/java/com/leecardo/paymentdiagnostics/domain/DiagnosticFactsTest.java`

- [ ] **Step 1: Write failing fact-invariant tests**

Cover non-negative payment amount, timestamp ordering, explicit provider error for `FAILED`, message transition timestamps, retry bounds, and nonblank trace correlation ID.

```java
assertThrows(IllegalArgumentException.class,
        () -> compensation("cmp-1", 4, 3, CompensationStatus.RETRIES_EXHAUSTED));
```

- [ ] **Step 2: Verify tests fail**

Run: `mvn -pl backend/agent-domain -am test`

Expected: compilation failure for missing types.

- [ ] **Step 3: Implement exact fact shapes**

```java
public enum PaymentStatus { REQUESTED, PROCESSING, PROVIDER_SUCCEEDED, CALLBACK_RECEIVED, FAILED }
public record PaymentTransaction(String transactionId, OrderId orderId, String provider,
        BigDecimal amount, PaymentStatus status, Instant requestedAt,
        Instant providerCompletedAt, Instant callbackReceivedAt,
        String providerErrorCode, String providerErrorSummary) { }

public enum MessageDeliveryStatus { PENDING, SENT, SEND_FAILED, CONSUMED, CONSUME_FAILED }
public record MessageDelivery(String deliveryId, OrderId orderId, String eventType,
        String correlationId, MessageDeliveryStatus status, Instant createdAt,
        Instant sentAt, Instant consumedAt, String lastError) { }

public enum CompensationStatus { PENDING, RUNNING, SUCCEEDED, FAILED, RETRIES_EXHAUSTED }
public record CompensationTask(String taskId, OrderId orderId, String action,
        CompensationStatus status, int retryCount, int maxRetries,
        Instant createdAt, Instant lastAttemptAt, String lastError) { }

public record TraceSummary(String traceId, OrderId orderId, String correlationId,
        Instant startedAt, Instant endedAt, boolean complete, String summary) { }
```

Keep constructors deterministic and side-effect free. `FAILED` payment requires error code and summary. Failed message/compensation states require `lastError`. Exhausted compensation requires `retryCount == maxRetries`.

- [ ] **Step 4: Verify domain facts**

Run: `mvn -pl backend/agent-domain -am test`

Expected: all fact tests pass.

### Task 3: Define evidence and diagnosis result contracts

**Files:**
- Modify: `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/DiagnosisEvidence.java`
- Modify: `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/DiagnosisStage.java`
- Create: `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/DiagnosisRuleId.java`
- Create: `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/DataMode.java`
- Create: `backend/agent-domain/src/main/java/com/leecardo/paymentdiagnostics/domain/DiagnosisResult.java`
- Modify: `backend/agent-domain/src/test/java/com/leecardo/paymentdiagnostics/domain/DiagnosisEvidenceTest.java`
- Test: `backend/agent-domain/src/test/java/com/leecardo/paymentdiagnostics/domain/DiagnosisResultTest.java`

- [ ] **Step 1: Update tests for stable evidence IDs and result invariants**

Require nonblank `id`, `source`, and `summary`. Require nonempty evidence for every rule except `NO_KNOWN_EXCEPTION` and `INSUFFICIENT_EVIDENCE`; require warnings to be immutable normalized text.

- [ ] **Step 2: Verify red state**

Run: `mvn -pl backend/agent-domain -am test`

Expected: constructor mismatch and missing result types.

- [ ] **Step 3: Implement clean-cutover result types**

```java
public record DiagnosisEvidence(String id, String source, String summary, Instant observedAt) { }
public enum DataMode { SIMULATION, POSTGRES }
public enum DiagnosisRuleId {
    NO_KNOWN_EXCEPTION,
    PAYMENT_NOT_STARTED,
    PAYMENT_PROCESSING_TIMEOUT,
    PROVIDER_SUCCEEDED_CALLBACK_MISSING,
    CALLBACK_SUCCEEDED_ORDER_NOT_UPDATED,
    PAYMENT_FAILED_WITH_PROVIDER_ERROR,
    MESSAGE_NOT_SENT,
    MESSAGE_SEND_FAILED,
    MESSAGE_NOT_CONSUMED,
    MESSAGE_CONSUME_FAILED,
    COMPENSATION_NOT_CREATED,
    COMPENSATION_FAILED,
    COMPENSATION_RETRIES_EXHAUSTED,
    TRACE_MISSING,
    INSUFFICIENT_EVIDENCE
}
public record DiagnosisResult(OrderId orderId, DataMode dataMode, DiagnosisStage stage,
        DiagnosisRuleId ruleId, String summary,
        List<DiagnosisEvidence> evidence, List<String> warnings) { }
```

Add `PAYMENT_CALLBACK`, `ORDER_STATE_UPDATE`, and `COMPLETED` to `DiagnosisStage`. Use `List.copyOf` in `DiagnosisResult`.

- [ ] **Step 4: Verify domain contract**

Run: `mvn -pl backend/agent-domain -am test`

Expected: all domain tests pass, including migrated `DiagnosisEvidenceTest`.

### Task 4: Define application ports and order lookup

**Files:**
- Modify: `backend/agent-application/pom.xml`
- Create: five interfaces under `backend/agent-application/src/main/java/com/leecardo/paymentdiagnostics/application/port/`
- Create: `FactQueryException.java` in that package
- Create: `backend/agent-application/src/main/java/com/leecardo/paymentdiagnostics/application/order/GetOrderUseCase.java`
- Create: `backend/agent-application/src/main/java/com/leecardo/paymentdiagnostics/application/order/OrderNotFoundException.java`
- Test: `backend/agent-application/src/test/java/com/leecardo/paymentdiagnostics/application/order/GetOrderUseCaseTest.java`

- [ ] **Step 1: Add JUnit test dependency and write order-use-case tests**

Use hand-written fake ports, not Mockito. Test found, missing, invalid ID, unavailable, and timeout propagation.

- [ ] **Step 2: Verify red state**

Run: `mvn -pl backend/agent-application -am test`

Expected: compilation fails for missing use case/port.

- [ ] **Step 3: Implement ports and exceptions**

```java
public interface OrderQueryPort { Optional<OrderSnapshot> findById(OrderId orderId); }
public interface PaymentQueryPort { List<PaymentTransaction> findByOrderId(OrderId orderId); }
public interface MessageQueryPort { List<MessageDelivery> findByOrderId(OrderId orderId); }
public interface CompensationQueryPort { List<CompensationTask> findByOrderId(OrderId orderId); }
public interface TraceQueryPort { Optional<TraceSummary> findByOrderId(OrderId orderId); }

public final class FactQueryException extends RuntimeException {
    public enum Kind { UNAVAILABLE, TIMEOUT }
    private final Kind kind;
}
```

`GetOrderUseCase.get(String)` constructs `OrderId`, queries the port, and throws `OrderNotFoundException` only for an empty successful query.

- [ ] **Step 4: Verify application order lookup**

Run: `mvn -pl backend/agent-application -am test`

Expected: all tests pass.

### Task 5: Implement ordered deterministic diagnosis rules

**Files:**
- Create: `backend/agent-application/src/main/java/com/leecardo/paymentdiagnostics/application/diagnosis/DiagnosisPolicy.java`
- Create: `backend/agent-application/src/main/java/com/leecardo/paymentdiagnostics/application/diagnosis/CollectedFacts.java`
- Create: `backend/agent-application/src/main/java/com/leecardo/paymentdiagnostics/application/diagnosis/DeterministicDiagnosisRules.java`
- Test: `backend/agent-application/src/test/java/com/leecardo/paymentdiagnostics/application/diagnosis/DeterministicDiagnosisRulesTest.java`

- [ ] **Step 1: Write one failing hit test per rule plus boundary counterexamples**

Use a fixed observation time. Cover exact timeout boundaries (`age == threshold` is not timed out; `age > threshold` is), payment-rule precedence over message/compensation, exhausted precedence over retryable compensation failure, and normal complete flow.

- [ ] **Step 2: Verify red state**

Run: `mvn -pl backend/agent-application -am test`

Expected: missing rule engine.

- [ ] **Step 3: Implement policy and collected facts**

```java
public record DiagnosisPolicy(Duration paymentProcessingTimeout,
        Duration messageConsumptionTimeout) { }

public record CollectedFacts(OrderSnapshot order,
        List<PaymentTransaction> payments,
        List<MessageDelivery> messages,
        List<CompensationTask> compensations,
        Optional<TraceSummary> trace,
        Instant observedAt,
        DataMode dataMode,
        List<String> warnings) { }
```

- [ ] **Step 4: Implement rules in specification order**

`DeterministicDiagnosisRules.diagnose(CollectedFacts)` must create evidence IDs from fact IDs, e.g. `order:<orderId>`, `payment:<transactionId>`, `message:<deliveryId>`. It must never inspect scenario names. `MESSAGE_NOT_SENT` and `COMPENSATION_NOT_CREATED` apply only when earlier facts prove those actions were expected. `TRACE_MISSING` is a gap result only when no stronger rule matches.

- [ ] **Step 5: Verify every rule and precedence**

Run: `mvn -pl backend/agent-application -am test`

Expected: all rule tests pass.

### Task 6: Implement the fixed evidence-collection workflow

**Files:**
- Create: `backend/agent-application/src/main/java/com/leecardo/paymentdiagnostics/application/diagnosis/DiagnosePaymentExceptionUseCase.java`
- Test: `backend/agent-application/src/test/java/com/leecardo/paymentdiagnostics/application/diagnosis/DiagnosePaymentExceptionUseCaseTest.java`

- [ ] **Step 1: Write workflow tests**

Verify query order, missing order short-circuit, deterministic repeat execution, optional trace absence, and that any downstream `FactQueryException` becomes a `503`-eligible failure rather than a missing-business-record rule.

- [ ] **Step 2: Verify red state**

Run: `mvn -pl backend/agent-application -am test`

Expected: missing workflow.

- [ ] **Step 3: Implement the workflow**

Constructor dependencies: all five ports, `DeterministicDiagnosisRules`, `DiagnosisPolicy`, `Clock`, and `DataMode`. `diagnose(String)` creates `OrderId`, loads facts in the approved order, obtains `clock.instant()` once, constructs `CollectedFacts`, and delegates to rules.

- [ ] **Step 4: Verify application module**

Run: `mvn -pl backend/agent-application -am test`

Expected: all order, workflow, and rule tests pass.

### Task 7: Build the versioned simulation document and immutable store

**Files:**
- Modify: `backend/agent-infrastructure/pom.xml`
- Create: `SimulationScenarioDocument.java`, `SimulationScenarioLoader.java`, `SimulationFactStore.java`
- Create: `backend/agent-infrastructure/src/test/resources/simulation/invalid-schema.json`
- Test: `backend/agent-infrastructure/src/test/java/com/leecardo/paymentdiagnostics/infrastructure/simulation/SimulationScenarioLoaderTest.java`

- [ ] **Step 1: Add dependencies and failing loader tests**

Add `jackson-databind`, `spring-context`, and `spring-boot-starter-test` test scope. Tests must reject unknown schema version, duplicate order/transaction/delivery/task IDs, broken order references, invalid enum values, negative amounts, and inconsistent timestamps.

- [ ] **Step 2: Verify red state**

Run: `mvn -pl backend/agent-infrastructure -am test`

Expected: missing loader/store types.

- [ ] **Step 3: Implement document DTOs and loader**

Top-level JSON shape:

```json
{
  "schemaVersion": 1,
  "observedAt": "2026-08-17T02:00:00Z",
  "orders": [],
  "paymentTransactions": [],
  "messageDeliveries": [],
  "compensationTasks": [],
  "traceSummaries": [],
  "failures": []
}
```

`failures` entries contain only `source`, `orderId`, and `kind` (`UNAVAILABLE` or `TIMEOUT`) so failure behavior is deterministic. The loader maps DTOs into domain constructors; validation errors include JSON resource name and logical object ID, never an absolute filesystem path.

- [ ] **Step 4: Implement one immutable five-port store**

`SimulationFactStore` implements all five query ports. Build immutable maps grouped by `OrderId`; preserve document order with `List.copyOf`. Before each query, consult the keyed failure map and throw `FactQueryException`. Empty successful list queries return `List.of()`.

- [ ] **Step 5: Verify loader/store behavior**

Run: `mvn -pl backend/agent-infrastructure -am test`

Expected: loader/store tests pass.

### Task 8: Add complete deterministic scenario data and profile configuration

**Files:**
- Create: `backend/agent-infrastructure/src/main/resources/simulation/payment-diagnosis-scenarios.json`
- Create: `backend/agent-infrastructure/src/main/java/com/leecardo/paymentdiagnostics/infrastructure/simulation/SimulationConfiguration.java`
- Test: `backend/agent-infrastructure/src/test/java/com/leecardo/paymentdiagnostics/infrastructure/simulation/SimulationScenarioCoverageTest.java`

- [ ] **Step 1: Write a failing scenario coverage test**

Require the 15 approved scenario IDs, unique facts, and no fields named `diagnosis`, `ruleId`, `reason`, `customerName`, `phone`, `address`, `token`, `secret`, or `credential`.

- [ ] **Step 2: Add the scenario document**

Use fixed safe IDs such as `SIM-NORMAL-001`, `SIM-PAY-NOT-STARTED-001`, and `SIM-COMP-EXHAUSTED-001`. Use synthetic product names and provider error `SIM_DECLINED`; never copy values from the reference system’s test JWT or personal fields.

- [ ] **Step 3: Add profile-gated configuration**

```java
@Configuration(proxyBeanMethods = false)
@Profile("simulation")
public class SimulationConfiguration {
    @Bean
    SimulationFactStore simulationFactStore(ObjectMapper mapper,
            @Value("${app.simulation.scenarios:classpath:simulation/payment-diagnosis-scenarios.json}") Resource resource) {
        return new SimulationScenarioLoader(mapper).load(resource);
    }
}
```

Expose the same store bean through its five port interfaces without duplicating loaded data. No simulation bean exists outside the profile.

- [ ] **Step 4: Verify scenario coverage**

Run: `mvn -pl backend/agent-infrastructure -am test`

Expected: all 15 scenarios load and coverage/security checks pass.

### Task 9: Wire use cases and stable REST error contracts

**Files:**
- Create: `backend/agent-api/src/main/java/com/leecardo/paymentdiagnostics/api/config/DiagnosticUseCaseConfiguration.java`
- Create: `backend/agent-api/src/main/java/com/leecardo/paymentdiagnostics/api/error/ApiError.java`
- Create: `backend/agent-api/src/main/java/com/leecardo/paymentdiagnostics/api/error/ApiExceptionHandler.java`
- Modify: `backend/agent-api/src/main/java/com/leecardo/paymentdiagnostics/api/AgentApiApplication.java`
- Modify: `backend/agent-api/src/main/resources/application.yml`
- Test: `backend/agent-api/src/test/java/com/leecardo/paymentdiagnostics/api/error/ApiExceptionHandlerTest.java`

- [ ] **Step 1: Write failing exception contract tests**

Require stable responses:

```json
{"code":"INVALID_ORDER_ID","message":"orderId is invalid"}
{"code":"ORDER_NOT_FOUND","message":"order was not found"}
{"code":"FACT_SOURCE_UNAVAILABLE","message":"diagnostic fact source is unavailable"}
{"code":"FACT_SOURCE_TIMEOUT","message":"diagnostic fact source timed out"}
```

Assert no `exception`, `trace`, `sql`, or absolute path fields.

- [ ] **Step 2: Verify red state**

Run: `mvn -pl backend/agent-api -am test`

Expected: missing handler/config.

- [ ] **Step 3: Implement configuration and handler**

Annotate `DiagnosticUseCaseConfiguration` with `@Configuration(proxyBeanMethods = false)`, `@Profile("simulation")`, and `@EnableConfigurationProperties(DiagnosisProperties.class)`. Create `DiagnosisProperties` with `Duration paymentProcessingTimeout` and `Duration messageConsumptionTimeout`. Its beans construct `DeterministicDiagnosisRules`, `GetOrderUseCase`, and `DiagnosePaymentExceptionUseCase` with `Clock.systemUTC()` and `DataMode.SIMULATION`. Annotate both controllers with `@Profile("simulation")`; controllers never inspect active profiles. `ApiExceptionHandler` maps `IllegalArgumentException`, `OrderNotFoundException`, and both `FactQueryException.Kind` values to the four stable error records from Step 1.

- [ ] **Step 4: Update application imports and properties**

Import `AiModelConfiguration`, `SimulationConfiguration`, and `DiagnosticUseCaseConfiguration`. Add `app.diagnosis.payment-processing-timeout: 15m`, `app.diagnosis.message-consumption-timeout: 5m`, and the scenario classpath location to YAML; do not add `spring.profiles.active`.

- [ ] **Step 5: Verify error contracts**

Run: `mvn -pl backend/agent-api -am test`

Expected: handler tests and existing status test pass.

### Task 10: Expose safe order and diagnosis APIs

**Files:**
- Create: `backend/agent-api/src/main/java/com/leecardo/paymentdiagnostics/api/order/OrderController.java`
- Create: `backend/agent-api/src/main/java/com/leecardo/paymentdiagnostics/api/diagnosis/DiagnosisController.java`
- Test: `backend/agent-api/src/test/java/com/leecardo/paymentdiagnostics/api/order/OrderControllerTest.java`
- Test: `backend/agent-api/src/test/java/com/leecardo/paymentdiagnostics/api/diagnosis/DiagnosisControllerTest.java`

- [ ] **Step 1: Write failing MockMvc contract tests**

Use `@WebMvcTest` plus imported handler and mocked use cases. Assert exact safe fields, ISO-8601 times, two-decimal monetary JSON numbers, `dataMode=SIMULATION`, evidence IDs, warnings, `400`, `404`, and `503`. Assert sensitive names do not occur in serialized responses.

- [ ] **Step 2: Verify red state**

Run: `mvn -pl backend/agent-api -am test`

Expected: missing controllers.

- [ ] **Step 3: Implement controllers with explicit response records**

Controllers map domain records to package-local response records. Do not serialize infrastructure DTOs or reference-system entities. Endpoints are exactly:

```java
@GetMapping("/api/orders/{orderId}")
@GetMapping("/api/diagnoses/orders/{orderId}")
```

- [ ] **Step 4: Verify API contracts**

Run: `mvn -pl backend/agent-api -am test`

Expected: API tests pass; existing `/api/status` response remains unchanged.

### Task 11: Add PostgreSQL fact schema and idempotent demo data

**Files:**
- Create: `backend/agent-api/src/main/resources/db/migration/V2__create_diagnostic_fact_tables.sql`
- Create: `deploy/postgres/demo/001_payment_diagnosis_scenarios.sql`
- Test: `backend/agent-infrastructure/src/test/java/com/leecardo/paymentdiagnostics/infrastructure/simulation/DeploymentAssetConsistencyTest.java`

- [ ] **Step 1: Write failing static consistency tests**

Load both SQL files as text and verify all five table names, required foreign keys/index names, no forbidden identity columns, and that every simulation order ID appears in demo SQL. Parse the SQL INSERT tuples enough to compare order IDs and enum strings; do not claim PostgreSQL parsing.

- [ ] **Step 2: Create the Flyway migration**

Use PostgreSQL-native `varchar`, `numeric(10,2)`, `integer`, `timestamptz`, `boolean`, named constraints, and named indexes. Include exact `CHECK` sets matching Java enums. Foreign keys reference `orders(order_id)`; facts use `ON DELETE RESTRICT`. Do not use MySQL syntax or copy encrypted legacy identity columns.

- [ ] **Step 3: Create optional demo SQL**

Header states prerequisite `V2` and explicit execution only. Use fixed timestamps/IDs matching JSON. Use `INSERT ... ON CONFLICT (primary_key) DO UPDATE SET ...` so repeated imports converge. Insert parent orders before dependent facts.

- [ ] **Step 4: Verify asset consistency**

Run: `mvn -pl backend/agent-infrastructure -am test`

Expected: SQL and scenario consistency test passes; output must not claim real PostgreSQL execution.

### Task 12: Add vendor-neutral message contracts

**Files:**
- Create: `deploy/messaging/payment-events.schema.json`
- Create: `deploy/messaging/topology.json`
- Extend test: `DeploymentAssetConsistencyTest.java`

- [ ] **Step 1: Add failing JSON contract assertions**

Require three event names, schema version `1`, required envelope fields, producer/consumer names, idempotency key, retry intent, dead-letter intent, and forbidden sensitive field list.

- [ ] **Step 2: Create the event JSON Schema**

Use JSON Schema draft 2020-12. Envelope requires `eventId`, `eventType`, `eventVersion`, `orderId`, `correlationId`, `occurredAt`, and `payload`. Restrict `eventType` to `payment.confirmed`, `order.state-update-requested`, and `order.state-updated`. Set `additionalProperties: false` at envelope and payload boundaries.

- [ ] **Step 3: Create logical topology**

Declare logical channels and consumers without RabbitMQ exchanges, RocketMQ groups, or Kafka partitions. Include bounded retry intent and dead-letter logical channel. Add a top-level statement that this is vendor-neutral and not directly executable.

- [ ] **Step 4: Verify JSON assets**

Run: `mvn -pl backend/agent-infrastructure -am test`

Expected: Jackson parses both files and all contract assertions pass.

### Task 13: Run the backend simulation smoke path

**Files:**
- No source files unless a behavior found here requires correction.

- [ ] **Step 1: Run full automated verification**

Run: `mvn verify`

Expected: all seven reactor projects succeed; all new and existing tests pass.

- [ ] **Step 2: Start the actual API with simulation profile**

Run as a supervised process:

```bash
mvn -pl backend/agent-api -am spring-boot:run -Dspring-boot.run.profiles=simulation
```

Readiness: log contains `Started AgentApiApplication`, port `8080` accepts connections.

- [ ] **Step 3: Exercise actual HTTP behavior**

Call:

```bash
curl -fsS http://localhost:8080/api/orders/SIM-NORMAL-001
curl -fsS http://localhost:8080/api/diagnoses/orders/SIM-PAY-CALLBACK-MISSING-001
curl -sS -o /tmp/not-found.json -w '%{http_code}' http://localhost:8080/api/orders/SIM-NOT-FOUND-001
```

Expected: safe order JSON; diagnosis has `SIMULATION`, `PROVIDER_SUCCEEDED_CALLBACK_MISSING`, and evidence IDs; missing order returns `404`. Inspect output for absence of personal/sensitive fields.

- [ ] **Step 4: Confirm default mode does not expose simulation**

Stop the profile process, start without `simulation`, and require `/api/status` to return `200` while `/api/orders/SIM-NORMAL-001` and `/api/diagnoses/orders/SIM-NORMAL-001` return `404`. Add an `ApplicationContextRunner` or `@SpringBootTest` context test in Task 9 that asserts no five query-port beans, diagnostic use cases, or diagnostic controllers exist without the profile.

- [ ] **Step 5: Stop the supervised API process**

Stop it cleanly and retain smoke outputs as verification evidence.

### Task 14: Update roadmap truthfully after verification

**Files:**
- Modify: `docs/roadmap/development-roadmap.md`

- [ ] **Step 1: Record only verified simulation progress**

Add a short status subsection under the current baseline/M1–M3 areas stating:

- simulation profile and deterministic backend paths are implemented and verified;
- PostgreSQL schema/demo SQL and vendor-neutral message contracts are prepared;
- real PostgreSQL/Flyway execution, Testcontainers, real MQ topology, frontend, and browser verification remain open;
- simulation results are visibly marked and are not production evidence.

Do not mark M1, M2, or M3 fully complete because their real middleware and frontend completion criteria remain unmet.

- [ ] **Step 2: Re-run final verification after documentation change**

Run: `mvn verify`

Expected: reactor success with all tests passing.

- [ ] **Step 3: Report exact evidence and remaining limits**

Report test totals from Maven output, actual HTTP responses exercised, and explicitly state that PostgreSQL and MQ assets were not executed against real middleware because those services were intentionally skipped.
