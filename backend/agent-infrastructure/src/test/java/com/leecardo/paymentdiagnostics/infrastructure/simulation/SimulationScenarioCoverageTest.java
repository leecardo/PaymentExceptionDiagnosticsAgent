package com.leecardo.paymentdiagnostics.infrastructure.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leecardo.paymentdiagnostics.application.diagnosis.DiagnosePaymentExceptionUseCase;
import com.leecardo.paymentdiagnostics.application.diagnosis.DeterministicDiagnosisRules;
import com.leecardo.paymentdiagnostics.application.diagnosis.DiagnosisPolicy;
import com.leecardo.paymentdiagnostics.application.order.OrderNotFoundException;
import com.leecardo.paymentdiagnostics.domain.CompensationStatus;
import com.leecardo.paymentdiagnostics.domain.CompensationTask;
import com.leecardo.paymentdiagnostics.domain.DataMode;
import com.leecardo.paymentdiagnostics.domain.DiagnosisResult;
import com.leecardo.paymentdiagnostics.domain.DiagnosisRuleId;
import com.leecardo.paymentdiagnostics.domain.MessageDelivery;
import com.leecardo.paymentdiagnostics.domain.MessageDeliveryStatus;
import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;
import com.leecardo.paymentdiagnostics.domain.OrderStatus;
import com.leecardo.paymentdiagnostics.domain.PaymentStatus;
import com.leecardo.paymentdiagnostics.domain.PaymentTransaction;
import com.leecardo.paymentdiagnostics.domain.TraceSummary;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SimulationScenarioCoverageTest {

    private static final String RESOURCE_PATH = "simulation/payment-diagnosis-scenarios.json";
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-17T12:00:00Z");
    private static final String ABSENT_ORDER_ID = "SIM-ORDER-NOT-FOUND-001";
    private static final DiagnosisPolicy POLICY = new DiagnosisPolicy(Duration.ofMinutes(10), Duration.ofMinutes(5));

    private static final List<String> APPROVED_QUERY_IDS = List.of(
            "SIM-NORMAL-001",
            ABSENT_ORDER_ID,
            "SIM-PAY-NOT-STARTED-001",
            "SIM-PAY-TIMEOUT-001",
            "SIM-CALLBACK-MISSING-001",
            "SIM-ORDER-NOT-UPDATED-001",
            "SIM-PROVIDER-FAILED-001",
            "SIM-MESSAGE-NOT-SENT-001",
            "SIM-MESSAGE-SEND-FAILED-001",
            "SIM-MESSAGE-NOT-CONSUMED-001",
            "SIM-MESSAGE-CONSUME-FAILED-001",
            "SIM-COMP-NOT-CREATED-001",
            "SIM-COMP-FAILED-001",
            "SIM-COMP-EXHAUSTED-001",
            "SIM-TRACE-MISSING-INSUFFICIENT-001");

    private static final Map<String, DiagnosisRuleId> EXPECTED_PRESENT_RULES = expectedPresentRules();
    private static final List<String> BANNED_SUBSTRINGS = List.of(
            "customername",
            "realname",
            "username",
            "phone",
            "address",
            "token",
            "secret",
            "credential",
            "jwt",
            "diagnosis",
            "rule",
            "ruleid",
            "reason");

    @Test
    void productionResourceLoadsWithApprovedScenarioIdsAndFactCoverage() {
        SimulationScenarioDocument document = loadDocument();

        assertEquals(1, document.schemaVersion());
        assertEquals(OBSERVED_AT, document.observedAt());
        assertEquals(15, APPROVED_QUERY_IDS.size());
        assertEquals(Set.copyOf(APPROVED_QUERY_IDS).size(), APPROVED_QUERY_IDS.size());

        Set<String> presentOrderIds = new LinkedHashSet<>();
        document.orders().forEach(order -> presentOrderIds.add(order.orderId().value()));
        assertEquals(EXPECTED_PRESENT_RULES.keySet(), presentOrderIds);
        assertFalse(presentOrderIds.contains(ABSENT_ORDER_ID));
        assertTrue(document.failures().isEmpty());

        assertAllFactIdsAreUnique(document);
        assertEveryFactReferencesPresentOrder(document, presentOrderIds);
        assertScenarioFactsTriggerTheirIntendedRules(document);
    }

    @Test
    void productionJsonContainsOnlySafeFactFieldsAndValues() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream input = new ClassPathResource(RESOURCE_PATH).getInputStream()) {
            assertNoBannedText(mapper.readTree(input), "$", false);
        }
    }

    @Test
    void scenariosProduceIntendedDeterministicResultsThroughStorePortsAndFixedClock() {
        SimulationFactStore store = loadStore();
        DiagnosePaymentExceptionUseCase useCase = useCase(store);

        Map<String, DiagnosisRuleId> firstRun = diagnoseAllPresent(useCase);
        Map<String, DiagnosisRuleId> secondRun = diagnoseAllPresent(useCase);

        assertEquals(EXPECTED_PRESENT_RULES, firstRun);
        assertEquals(firstRun, secondRun);

        for (String orderId : EXPECTED_PRESENT_RULES.keySet()) {
            DiagnosisResult first = useCase.diagnose(orderId);
            DiagnosisResult second = useCase.diagnose(orderId);
            assertEquals(first, second);
            assertEquals(DataMode.SIMULATION, first.dataMode());
            assertEquals(new OrderId(orderId), first.orderId());
        }

        OrderId absentOrderId = new OrderId(ABSENT_ORDER_ID);
        assertTrue(store.findById(absentOrderId).isEmpty());
        assertTrue(store.paymentQueryPort().findByOrderId(absentOrderId).isEmpty());
        assertTrue(store.messageQueryPort().findByOrderId(absentOrderId).isEmpty());
        assertTrue(store.compensationQueryPort().findByOrderId(absentOrderId).isEmpty());
        assertTrue(store.traceQueryPort().findByOrderId(absentOrderId).isEmpty());

        OrderNotFoundException thrown = assertThrows(OrderNotFoundException.class, () -> useCase.diagnose(absentOrderId.value()));
        assertEquals(absentOrderId, thrown.orderId());
    }

    private static void assertScenarioFactsTriggerTheirIntendedRules(SimulationScenarioDocument document) {
        assertEquals(OrderStatus.PAID, orderById(document, "SIM-NORMAL-001").status());
        assertEquals(PaymentStatus.CALLBACK_RECEIVED, onlyPayment(document, "SIM-NORMAL-001").status());
        assertEquals(MessageDeliveryStatus.CONSUMED, onlyMessage(document, "SIM-NORMAL-001").status());
        assertEquals(true, traceByOrderId(document, "SIM-NORMAL-001").orElseThrow().complete());

        assertEquals(OrderStatus.PENDING_PAYMENT, orderById(document, "SIM-PAY-NOT-STARTED-001").status());
        assertTrue(paymentsFor(document, "SIM-PAY-NOT-STARTED-001").isEmpty());

        PaymentTransaction timedOut = onlyPayment(document, "SIM-PAY-TIMEOUT-001");
        assertEquals(PaymentStatus.PROCESSING, timedOut.status());
        assertTrue(timedOut.requestedAt().plus(POLICY.paymentProcessingTimeout()).isBefore(document.observedAt()));

        PaymentTransaction callbackMissing = onlyPayment(document, "SIM-CALLBACK-MISSING-001");
        assertEquals(PaymentStatus.PROVIDER_SUCCEEDED, callbackMissing.status());
        assertEquals(null, callbackMissing.callbackReceivedAt());

        assertEquals(OrderStatus.PENDING_PAYMENT, orderById(document, "SIM-ORDER-NOT-UPDATED-001").status());
        assertEquals(PaymentStatus.CALLBACK_RECEIVED, onlyPayment(document, "SIM-ORDER-NOT-UPDATED-001").status());

        PaymentTransaction providerFailed = onlyPayment(document, "SIM-PROVIDER-FAILED-001");
        assertEquals(PaymentStatus.FAILED, providerFailed.status());
        assertEquals("SIM_DECLINED", providerFailed.providerErrorCode());

        assertEquals(OrderStatus.PAID, orderById(document, "SIM-MESSAGE-NOT-SENT-001").status());
        assertEquals(PaymentStatus.CALLBACK_RECEIVED, onlyPayment(document, "SIM-MESSAGE-NOT-SENT-001").status());
        assertTrue(messagesFor(document, "SIM-MESSAGE-NOT-SENT-001").isEmpty());

        assertEquals(MessageDeliveryStatus.SEND_FAILED, onlyMessage(document, "SIM-MESSAGE-SEND-FAILED-001").status());

        MessageDelivery notConsumed = onlyMessage(document, "SIM-MESSAGE-NOT-CONSUMED-001");
        assertEquals(MessageDeliveryStatus.SENT, notConsumed.status());
        assertTrue(notConsumed.sentAt().plus(POLICY.messageConsumptionTimeout()).isBefore(document.observedAt()));

        assertEquals(MessageDeliveryStatus.CONSUME_FAILED, onlyMessage(document, "SIM-MESSAGE-CONSUME-FAILED-001").status());

        assertEquals(OrderStatus.CANCELLED, orderById(document, "SIM-COMP-NOT-CREATED-001").status());
        assertEquals(PaymentStatus.CALLBACK_RECEIVED, onlyPayment(document, "SIM-COMP-NOT-CREATED-001").status());
        assertTrue(compensationsFor(document, "SIM-COMP-NOT-CREATED-001").isEmpty());

        assertEquals(CompensationStatus.FAILED, onlyCompensation(document, "SIM-COMP-FAILED-001").status());
        CompensationTask exhausted = onlyCompensation(document, "SIM-COMP-EXHAUSTED-001");
        assertEquals(CompensationStatus.RETRIES_EXHAUSTED, exhausted.status());
        assertEquals(exhausted.maxRetries(), exhausted.retryCount());

        assertEquals(OrderStatus.OUTBOUND, orderById(document, "SIM-TRACE-MISSING-INSUFFICIENT-001").status());
        assertTrue(paymentsFor(document, "SIM-TRACE-MISSING-INSUFFICIENT-001").isEmpty());
        assertTrue(traceByOrderId(document, "SIM-TRACE-MISSING-INSUFFICIENT-001").isEmpty());
    }

    private static Map<String, DiagnosisRuleId> diagnoseAllPresent(DiagnosePaymentExceptionUseCase useCase) {
        Map<String, DiagnosisRuleId> results = new LinkedHashMap<>();
        EXPECTED_PRESENT_RULES.keySet().forEach(orderId -> results.put(orderId, useCase.diagnose(orderId).ruleId()));
        return results;
    }

    private static DiagnosePaymentExceptionUseCase useCase(SimulationFactStore store) {
        return new DiagnosePaymentExceptionUseCase(
                store.orderQueryPort(),
                store.paymentQueryPort(),
                store.messageQueryPort(),
                store.compensationQueryPort(),
                store.traceQueryPort(),
                new DeterministicDiagnosisRules(POLICY),
                Clock.fixed(store.observedAt(), ZoneOffset.UTC),
                DataMode.SIMULATION);
    }

    private static SimulationFactStore loadStore() {
        return new SimulationFactStore(loadDocument());
    }

    private static SimulationScenarioDocument loadDocument() {
        return new SimulationScenarioLoader(new ObjectMapper()).load(new ClassPathResource(RESOURCE_PATH));
    }

    private static Map<String, DiagnosisRuleId> expectedPresentRules() {
        Map<String, DiagnosisRuleId> rules = new LinkedHashMap<>();
        rules.put("SIM-NORMAL-001", DiagnosisRuleId.NO_KNOWN_EXCEPTION);
        rules.put("SIM-PAY-NOT-STARTED-001", DiagnosisRuleId.PAYMENT_NOT_STARTED);
        rules.put("SIM-PAY-TIMEOUT-001", DiagnosisRuleId.PAYMENT_PROCESSING_TIMEOUT);
        rules.put("SIM-CALLBACK-MISSING-001", DiagnosisRuleId.PROVIDER_SUCCEEDED_CALLBACK_MISSING);
        rules.put("SIM-ORDER-NOT-UPDATED-001", DiagnosisRuleId.CALLBACK_SUCCEEDED_ORDER_NOT_UPDATED);
        rules.put("SIM-PROVIDER-FAILED-001", DiagnosisRuleId.PAYMENT_FAILED_WITH_PROVIDER_ERROR);
        rules.put("SIM-MESSAGE-NOT-SENT-001", DiagnosisRuleId.MESSAGE_NOT_SENT);
        rules.put("SIM-MESSAGE-SEND-FAILED-001", DiagnosisRuleId.MESSAGE_SEND_FAILED);
        rules.put("SIM-MESSAGE-NOT-CONSUMED-001", DiagnosisRuleId.MESSAGE_NOT_CONSUMED);
        rules.put("SIM-MESSAGE-CONSUME-FAILED-001", DiagnosisRuleId.MESSAGE_CONSUME_FAILED);
        rules.put("SIM-COMP-NOT-CREATED-001", DiagnosisRuleId.COMPENSATION_NOT_CREATED);
        rules.put("SIM-COMP-FAILED-001", DiagnosisRuleId.COMPENSATION_FAILED);
        rules.put("SIM-COMP-EXHAUSTED-001", DiagnosisRuleId.COMPENSATION_RETRIES_EXHAUSTED);
        rules.put("SIM-TRACE-MISSING-INSUFFICIENT-001", DiagnosisRuleId.TRACE_MISSING);
        return Map.copyOf(rules);
    }

    private static void assertAllFactIdsAreUnique(SimulationScenarioDocument document) {
        Set<String> ids = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        document.orders().forEach(order -> addUnique(ids, duplicates, order.orderId().value()));
        document.paymentTransactions().forEach(payment -> addUnique(ids, duplicates, payment.transactionId()));
        document.messageDeliveries().forEach(message -> addUnique(ids, duplicates, message.deliveryId()));
        document.compensationTasks().forEach(task -> addUnique(ids, duplicates, task.taskId()));
        document.traceSummaries().forEach(trace -> addUnique(ids, duplicates, trace.traceId()));
        assertTrue(duplicates.isEmpty(), "duplicate fact ids " + duplicates);
        assertEquals(
                document.orders().size()
                        + document.paymentTransactions().size()
                        + document.messageDeliveries().size()
                        + document.compensationTasks().size()
                        + document.traceSummaries().size(),
                ids.size());
    }

    private static void addUnique(Set<String> ids, List<String> duplicates, String id) {
        if (!ids.add(id)) {
            duplicates.add(id);
        }
    }

    private static void assertEveryFactReferencesPresentOrder(SimulationScenarioDocument document, Set<String> presentOrderIds) {
        document.paymentTransactions().forEach(payment -> assertTrue(presentOrderIds.contains(payment.orderId().value())));
        document.messageDeliveries().forEach(message -> assertTrue(presentOrderIds.contains(message.orderId().value())));
        document.compensationTasks().forEach(task -> assertTrue(presentOrderIds.contains(task.orderId().value())));
        document.traceSummaries().forEach(trace -> assertTrue(presentOrderIds.contains(trace.orderId().value())));
    }

    private static void assertNoBannedText(JsonNode node, String path, boolean valueContext) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                assertSafeText(field.getKey(), path + "." + field.getKey(), false);
                assertNoBannedText(field.getValue(), path + "." + field.getKey(), true);
            }
            return;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                assertNoBannedText(node.get(index), path + "[" + index + "]", valueContext);
            }
            return;
        }
        if (node.isTextual()) {
            assertSafeText(node.textValue(), path, valueContext);
        }
    }

    private static void assertSafeText(String value, String path, boolean valueContext) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String banned : BANNED_SUBSTRINGS) {
            if (normalized.contains(banned)) {
                fail("banned " + (valueContext ? "value" : "field") + " substring '" + banned + "' at " + path);
            }
        }
    }

    private static OrderSnapshot orderById(SimulationScenarioDocument document, String orderId) {
        return document.orders().stream()
                .filter(order -> order.orderId().value().equals(orderId))
                .findFirst()
                .orElseThrow();
    }

    private static List<PaymentTransaction> paymentsFor(SimulationScenarioDocument document, String orderId) {
        return document.paymentTransactions().stream()
                .filter(payment -> payment.orderId().value().equals(orderId))
                .toList();
    }

    private static PaymentTransaction onlyPayment(SimulationScenarioDocument document, String orderId) {
        List<PaymentTransaction> payments = paymentsFor(document, orderId);
        assertEquals(1, payments.size());
        return payments.getFirst();
    }

    private static List<MessageDelivery> messagesFor(SimulationScenarioDocument document, String orderId) {
        return document.messageDeliveries().stream()
                .filter(message -> message.orderId().value().equals(orderId))
                .toList();
    }

    private static MessageDelivery onlyMessage(SimulationScenarioDocument document, String orderId) {
        List<MessageDelivery> messages = messagesFor(document, orderId);
        assertEquals(1, messages.size());
        return messages.getFirst();
    }

    private static List<CompensationTask> compensationsFor(SimulationScenarioDocument document, String orderId) {
        return document.compensationTasks().stream()
                .filter(compensation -> compensation.orderId().value().equals(orderId))
                .toList();
    }

    private static CompensationTask onlyCompensation(SimulationScenarioDocument document, String orderId) {
        List<CompensationTask> compensations = compensationsFor(document, orderId);
        assertEquals(1, compensations.size());
        return compensations.getFirst();
    }

    private static Optional<TraceSummary> traceByOrderId(SimulationScenarioDocument document, String orderId) {
        return document.traceSummaries().stream()
                .filter(trace -> trace.orderId().value().equals(orderId))
                .findFirst();
    }
}
