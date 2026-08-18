package com.leecardo.paymentdiagnostics.infrastructure.simulation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Validates consistency between simulation JSON scenarios, Flyway SQL,
 * demo data SQL, and vendor-neutral message contracts.
 *
 * This test does NOT execute SQL against PostgreSQL. It performs static
 * structural checks only.
 */
class DeploymentAssetConsistencyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<String> FORBIDDEN_COLUMN_FIELDS = Set.of(
            "customerName", "phone", "address", "email", "idCardNumber",
            "bankCardNumber", "token", "secret", "apiKey", "credential", "password",
            "diagnosis", "ruleId", "reason");

    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("SIM-[A-Z0-9-]+");

    // --- Flyway migration tests ---

    @Test
    void flywayMigrationContainsAllFiveTables() throws IOException {
        String sql = readFlywayMigration();

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS orders"),
                "Flyway migration must create orders table");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS payment_transactions"),
                "Flyway migration must create payment_transactions table");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS message_deliveries"),
                "Flyway migration must create message_deliveries table");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS compensation_tasks"),
                "Flyway migration must create compensation_tasks table");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS trace_summaries"),
                "Flyway migration must create trace_summaries table");
    }

    @Test
    void flywayMigrationContainsForeignKeysAndIndexes() throws IOException {
        String sql = readFlywayMigration();

        assertTrue(sql.contains("FOREIGN KEY"),
                "Flyway migration must define foreign keys");
        assertTrue(sql.contains("ON DELETE RESTRICT"),
                "Foreign keys must use ON DELETE RESTRICT");
        assertTrue(sql.contains("CONSTRAINT pk_orders PRIMARY KEY"),
                "orders must have named primary key");
        assertTrue(sql.contains("idx_payment_transactions_order_id"),
                "payment_transactions must have order_id index");
        assertTrue(sql.contains("idx_message_deliveries_order_id"),
                "message_deliveries must have order_id index");
        assertTrue(sql.contains("idx_compensation_tasks_order_id"),
                "compensation_tasks must have order_id index");
        assertTrue(sql.contains("idx_trace_summaries_order_id"),
                "trace_summaries must have order_id index");
    }

    @Test
    void flywayMigrationHasCheckConstraintsForEnums() throws IOException {
        String sql = readFlywayMigration();

        assertTrue(sql.contains("CHECK (role IN ('SINGLE', 'MASTER', 'SUB'))"),
                "orders must have role CHECK constraint");
        assertTrue(sql.contains("PENDING_PAYMENT"),
                "orders status CHECK must include PENDING_PAYMENT");
        assertTrue(sql.contains("CALLBACK_RECEIVED"),
                "payment_transactions status CHECK must include CALLBACK_RECEIVED");
        assertTrue(sql.contains("RETRIES_EXHAUSTED"),
                "compensation_tasks status CHECK must include RETRIES_EXHAUSTED");
    }

    @Test
    void flywayMigrationHasNoForbiddenIdentityColumns() throws IOException {
        String sql = readFlywayMigration();
        // Strip comments before checking for forbidden field names
        String stripped = sql.replaceAll("--.*", "");

        for (String forbidden : FORBIDDEN_COLUMN_FIELDS) {
            assertFalse(stripped.toLowerCase().contains(forbidden.toLowerCase()),
                    "Flyway migration must not contain forbidden field: " + forbidden);
        }
    }

    // --- Demo SQL tests ---

    @Test
    void everySimulationOrderIdAppearsInDemoSql() throws IOException {
        JsonNode scenarios = MAPPER.readTree(readSimulationJson());
        String demoSql = readDemoSql();

        Set<String> jsonOrderIds = new HashSet<>();
        for (JsonNode order : scenarios.get("orders")) {
            jsonOrderIds.add(order.get("orderId").asText());
        }

        assertFalse(jsonOrderIds.isEmpty(), "Simulation JSON must contain orders");

        Set<String> sqlOrderIds = extractOrderIds(demoSql);
        for (String orderId : jsonOrderIds) {
            assertTrue(sqlOrderIds.contains(orderId),
                    "Demo SQL must contain order ID: " + orderId);
        }
    }

    @Test
    void demoSqlContainsAllFiveTables() throws IOException {
        String demoSql = readDemoSql();

        assertTrue(demoSql.contains("INSERT INTO orders"),
                "Demo SQL must insert into orders");
        assertTrue(demoSql.contains("INSERT INTO payment_transactions"),
                "Demo SQL must insert into payment_transactions");
        assertTrue(demoSql.contains("INSERT INTO message_deliveries"),
                "Demo SQL must insert into message_deliveries");
        assertTrue(demoSql.contains("INSERT INTO compensation_tasks"),
                "Demo SQL must insert into compensation_tasks");
        assertTrue(demoSql.contains("INSERT INTO trace_summaries"),
                "Demo SQL must insert into trace_summaries");
    }

    @Test
    void demoSqlUsesIdempotentInsertOnConflict() throws IOException {
        String demoSql = readDemoSql();

        assertTrue(demoSql.contains("ON CONFLICT"),
                "Demo SQL must use ON CONFLICT for idempotency");
        assertTrue(demoSql.contains("DO UPDATE SET"),
                "Demo SQL must use DO UPDATE SET for idempotency");
    }

    @Test
    void demoSqlHasNoForbiddenFields() throws IOException {
        String demoSql = readDemoSql();
        // Strip SQL comments before checking for forbidden field names
        String stripped = demoSql.replaceAll("--.*", "");

        for (String forbidden : FORBIDDEN_COLUMN_FIELDS) {
            assertFalse(stripped.toLowerCase().contains(forbidden.toLowerCase()),
                    "Demo SQL must not contain forbidden field: " + forbidden);
        }
    }

    // --- Message event contract tests ---

    @Test
    void paymentEventsSchemaIsValidJson() throws IOException {
        JsonNode schema = MAPPER.readTree(readMessagingAsset("payment-events.schema.json"));

        assertTrue(schema.has("title"), "Schema must have title");
        assertTrue(schema.has("properties"), "Schema must have properties");
        assertTrue(schema.has("required"), "Schema must have required fields");

        JsonNode properties = schema.get("properties");
        for (String field : new String[]{"eventId", "eventType", "eventVersion", "orderId", "correlationId", "occurredAt", "payload"}) {
            assertTrue(properties.has(field), "Schema must define field: " + field);
        }

        JsonNode eventType = properties.get("eventType").get("enum");
        assertTrue(containsValue(eventType, "payment.confirmed"), "Schema must include payment.confirmed");
        assertTrue(containsValue(eventType, "order.state-update-requested"), "Schema must include order.state-update-requested");
        assertTrue(containsValue(eventType, "order.state-updated"), "Schema must include order.state-updated");

        int version = schema.get("properties").get("eventVersion").get("const").asInt();
        assertTrue(version == 1, "Schema version must be 1");
    }

    @Test
    void topologyJsonIsValid() throws IOException {
        JsonNode topology = MAPPER.readTree(readMessagingAsset("topology.json"));

        assertTrue(topology.has("logicalChannels"), "Topology must define logicalChannels");
        assertTrue(topology.has("eventBindings"), "Topology must define eventBindings");

        JsonNode channels = topology.get("logicalChannels");
        assertTrue(channels.isArray(), "logicalChannels must be array");
        assertTrue(channels.size() >= 3, "Must have at least 3 logical channels");

        boolean hasDeadLetter = false;
        for (JsonNode channel : channels) {
            if ("dead-letter".equals(channel.get("name").asText())) {
                hasDeadLetter = true;
                break;
            }
        }
        assertTrue(hasDeadLetter, "Topology must define dead-letter logical channel");

        JsonNode bindings = topology.get("eventBindings");
        assertTrue(bindings.isArray(), "eventBindings must be array");
        assertTrue(bindings.size() >= 3, "Must have at least 3 event bindings");

        for (JsonNode binding : bindings) {
            assertTrue(binding.has("retryPolicy"), "Each binding must have retryPolicy");
            JsonNode retry = binding.get("retryPolicy");
            assertTrue(retry.has("maxRetries"), "retryPolicy must have maxRetries");
            assertTrue(retry.get("maxRetries").asInt() >= 0, "maxRetries must be non-negative");
            assertTrue(retry.has("deadLetterChannel"), "retryPolicy must have deadLetterChannel");
        }

        assertTrue(topology.has("forbiddenPayloadFields"),
                "Topology must define forbiddenPayloadFields");
    }

    @Test
    void topologyContainsAllThreeEventTypes() throws IOException {
        JsonNode topology = MAPPER.readTree(readMessagingAsset("topology.json"));

        Set<String> eventTypes = new HashSet<>();
        for (JsonNode binding : topology.get("eventBindings")) {
            eventTypes.add(binding.get("eventType").asText());
        }
        assertTrue(eventTypes.contains("payment.confirmed"), "Topology must bind payment.confirmed");
        assertTrue(eventTypes.contains("order.state-update-requested"), "Topology must bind order.state-update-requested");
        assertTrue(eventTypes.contains("order.state-updated"), "Topology must bind order.state-updated");
    }

    // --- helpers ---

    private static Path projectRoot() {
        // user.dir during infrastructure tests is backend/agent-infrastructure
        return Paths.get(System.getProperty("user.dir")).resolve("..").resolve("..").toAbsolutePath();
    }

    private static String readFlywayMigration() throws IOException {
        Path path = projectRoot().resolve("backend/agent-api/src/main/resources/db/migration/V2__create_diagnostic_fact_tables.sql");
        assertTrue(Files.exists(path), "Flyway migration V2 must exist at " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String readSimulationJson() throws IOException {
        Path path = projectRoot().resolve("backend/agent-infrastructure/src/main/resources/simulation/payment-diagnosis-scenarios.json");
        assertTrue(Files.exists(path), "Simulation JSON must exist at " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String readDemoSql() throws IOException {
        Path path = projectRoot().resolve("deploy/postgres/demo/001_payment_diagnosis_scenarios.sql");
        assertTrue(Files.exists(path), "Demo SQL must exist at " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String readMessagingAsset(String filename) throws IOException {
        Path path = projectRoot().resolve("deploy/messaging/" + filename);
        assertTrue(Files.exists(path), "Messaging asset must exist at " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Set<String> extractOrderIds(String text) {
        Set<String> ids = new HashSet<>();
        Matcher matcher = ORDER_ID_PATTERN.matcher(text);
        while (matcher.find()) {
            ids.add(matcher.group());
        }
        return ids;
    }

    private static boolean containsValue(JsonNode array, String value) {
        if (array == null || !array.isArray()) {
            return false;
        }
        for (JsonNode node : array) {
            if (value.equals(node.asText())) {
                return true;
            }
        }
        return false;
    }
}
