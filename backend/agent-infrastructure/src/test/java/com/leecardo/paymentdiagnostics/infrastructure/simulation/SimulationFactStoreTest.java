package com.leecardo.paymentdiagnostics.infrastructure.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.leecardo.paymentdiagnostics.application.port.CompensationQueryPort;
import com.leecardo.paymentdiagnostics.application.port.MessageQueryPort;
import com.leecardo.paymentdiagnostics.application.port.PaymentQueryPort;
import com.leecardo.paymentdiagnostics.application.port.TraceQueryPort;
import com.leecardo.paymentdiagnostics.application.port.FactQueryException;
import com.leecardo.paymentdiagnostics.domain.CompensationStatus;
import com.leecardo.paymentdiagnostics.domain.CompensationTask;
import com.leecardo.paymentdiagnostics.domain.MessageDelivery;
import com.leecardo.paymentdiagnostics.domain.MessageDeliveryStatus;
import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.OrderRole;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;
import com.leecardo.paymentdiagnostics.domain.OrderStatus;
import com.leecardo.paymentdiagnostics.domain.PaymentStatus;
import com.leecardo.paymentdiagnostics.domain.PaymentTransaction;
import com.leecardo.paymentdiagnostics.domain.TraceSummary;

import org.junit.jupiter.api.Test;

class SimulationFactStoreTest {

    private static final Instant ORDERED_AT = Instant.parse("2026-08-17T10:00:00Z");
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-17T10:05:00Z");
    private static final Instant PROVIDER_COMPLETED_AT = Instant.parse("2026-08-17T10:06:00Z");
    private static final Instant CALLBACK_RECEIVED_AT = Instant.parse("2026-08-17T10:07:00Z");
    private static final Instant MESSAGE_CREATED_AT = Instant.parse("2026-08-17T10:08:00Z");
    private static final Instant SENT_AT = Instant.parse("2026-08-17T10:09:00Z");
    private static final Instant CONSUMED_AT = Instant.parse("2026-08-17T10:10:00Z");
    private static final Instant COMPENSATION_CREATED_AT = Instant.parse("2026-08-17T10:11:00Z");
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-17T10:12:00Z");

    @Test
    void mapsDocumentFactsAndQueriesByOrderWithoutPerQueryCopies() {
        SimulationFactStore store = new SimulationFactStore(new SimulationScenarioDocument(
                1,
                OBSERVED_AT,
                List.of(order("order-123"), order("order-456")),
                List.of(callbackPayment("payment-2", "order-123"), callbackPayment("payment-1", "order-123")),
                List.of(consumedMessage("message-1", "order-123")),
                List.of(succeededCompensation("compensation-1", "order-123")),
                List.of(trace("trace-1", "order-123")),
                List.of()));

        assertEquals(Optional.of(order("order-123")), store.findById(new OrderId("order-123")));
        assertEquals(Optional.empty(), store.findById(new OrderId("missing-order")));
        assertEquals(List.of(callbackPayment("payment-2", "order-123"), callbackPayment("payment-1", "order-123")),
                store.findPaymentsByOrderId(new OrderId("order-123")));
        assertEquals(List.of(consumedMessage("message-1", "order-123")), store.findMessagesByOrderId(new OrderId("order-123")));
        assertEquals(List.of(succeededCompensation("compensation-1", "order-123")), store.findCompensationsByOrderId(new OrderId("order-123")));
        assertEquals(Optional.of(trace("trace-1", "order-123")), store.findTraceByOrderId(new OrderId("order-123")));
        assertEquals(OBSERVED_AT, store.observedAt());
        PaymentQueryPort paymentQueryPort = store.paymentQueryPort();
        MessageQueryPort messageQueryPort = store.messageQueryPort();
        CompensationQueryPort compensationQueryPort = store.compensationQueryPort();
        TraceQueryPort traceQueryPort = store.traceQueryPort();

        assertSame(paymentQueryPort, store.paymentQueryPort());
        assertSame(messageQueryPort, store.messageQueryPort());
        assertSame(compensationQueryPort, store.compensationQueryPort());
        assertSame(traceQueryPort, store.traceQueryPort());
        assertEquals(List.of(callbackPayment("payment-2", "order-123"), callbackPayment("payment-1", "order-123")),
                paymentQueryPort.findByOrderId(new OrderId("order-123")));
        assertEquals(List.of(consumedMessage("message-1", "order-123")), messageQueryPort.findByOrderId(new OrderId("order-123")));
        assertEquals(List.of(succeededCompensation("compensation-1", "order-123")), compensationQueryPort.findByOrderId(new OrderId("order-123")));
        assertEquals(Optional.of(trace("trace-1", "order-123")), traceQueryPort.findByOrderId(new OrderId("order-123")));

        List<PaymentTransaction> first = store.findPaymentsByOrderId(new OrderId("order-123"));
        List<PaymentTransaction> second = store.findPaymentsByOrderId(new OrderId("order-123"));
        assertSame(first, second);
        assertThrows(UnsupportedOperationException.class, () -> first.add(callbackPayment("payment-3", "order-123")));
        assertSame(List.of(), store.findPaymentsByOrderId(new OrderId("missing-order")));
    }

    @Test
    void emptySuccessfulListsAndOptionalTraceAreStableEmptyResults() {
        SimulationFactStore store = new SimulationFactStore(new SimulationScenarioDocument(
                1,
                OBSERVED_AT,
                List.of(order("order-123")),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()));

        assertSame(List.of(), store.findPaymentsByOrderId(new OrderId("order-123")));
        assertSame(List.of(), store.findMessagesByOrderId(new OrderId("order-123")));
        assertSame(List.of(), store.findCompensationsByOrderId(new OrderId("order-123")));
        assertEquals(Optional.empty(), store.findTraceByOrderId(new OrderId("order-123")));
    }

    @Test
    void validatesDuplicateGlobalIdsAndBrokenReferences() {
        assertInvalid("duplicate orderId order-123", new SimulationScenarioDocument(1, OBSERVED_AT,
                List.of(order("order-123"), order("order-123")), List.of(), List.of(), List.of(), List.of(), List.of()));
        assertInvalid("duplicate transactionId payment-1", document(List.of(callbackPayment("payment-1", "order-123"), callbackPayment("payment-1", "order-123")), List.of(), List.of(), List.of(), List.of()));
        assertInvalid("duplicate deliveryId message-1", document(List.of(), List.of(consumedMessage("message-1", "order-123"), consumedMessage("message-1", "order-123")), List.of(), List.of(), List.of()));
        assertInvalid("duplicate taskId compensation-1", document(List.of(), List.of(), List.of(succeededCompensation("compensation-1", "order-123"), succeededCompensation("compensation-1", "order-123")), List.of(), List.of()));
        assertInvalid("duplicate traceId trace-1", document(List.of(), List.of(), List.of(), List.of(trace("trace-1", "order-123"), trace("trace-1", "order-123")), List.of()));
        assertInvalid("payment payment-1 references unknown orderId missing-order", document(List.of(callbackPayment("payment-1", "missing-order")), List.of(), List.of(), List.of(), List.of()));
        assertInvalid("message message-1 references unknown orderId missing-order", document(List.of(), List.of(consumedMessage("message-1", "missing-order")), List.of(), List.of(), List.of()));
        assertInvalid("compensation compensation-1 references unknown orderId missing-order", document(List.of(), List.of(), List.of(succeededCompensation("compensation-1", "missing-order")), List.of(), List.of()));
        assertInvalid("trace trace-1 references unknown orderId missing-order", document(List.of(), List.of(), List.of(), List.of(trace("trace-1", "missing-order")), List.of()));
        assertInvalid("duplicate trace for orderId order-123", document(List.of(), List.of(), List.of(), List.of(trace("trace-1", "order-123"), trace("trace-2", "order-123")), List.of()));
    }

    @Test
    void validatesFailuresForKnownOrdersAndOnePerSourceOrder() {
        assertInvalid("failure references unknown orderId missing-order", document(List.of(), List.of(), List.of(), List.of(),
                List.of(new SimulationScenarioDocument.FailureRecord("ORDER", "missing-order", "UNAVAILABLE"))));
        assertInvalid("duplicate failure for ORDER/order-123", document(List.of(), List.of(), List.of(), List.of(),
                List.of(new SimulationScenarioDocument.FailureRecord("ORDER", "order-123", "UNAVAILABLE"),
                        new SimulationScenarioDocument.FailureRecord("ORDER", "order-123", "TIMEOUT"))));
    }

    @Test
    void eachFailureSourceAndKindThrowsBeforeReturningFacts() {
        for (SimulationFactSource source : SimulationFactSource.values()) {
            for (FactQueryException.Kind kind : FactQueryException.Kind.values()) {
                SimulationFactStore store = new SimulationFactStore(new SimulationScenarioDocument(
                        1,
                        OBSERVED_AT,
                        List.of(order("order-123")),
                        List.of(callbackPayment("payment-1", "order-123")),
                        List.of(consumedMessage("message-1", "order-123")),
                        List.of(succeededCompensation("compensation-1", "order-123")),
                        List.of(trace("trace-1", "order-123")),
                        List.of(new SimulationScenarioDocument.FailureRecord(source.name(), "order-123", kind.name()))));

                FactQueryException thrown = assertThrows(FactQueryException.class,
                        () -> queryFailingSource(store, source, new OrderId("order-123")));

                assertEquals(kind, thrown.kind());
                assertEquals(source + " facts for orderId order-123 are " + kind, thrown.getMessage());
            }
        }
    }

    @Test
    void repeatedFailuresAreDeterministic() {
        SimulationFactStore store = new SimulationFactStore(new SimulationScenarioDocument(
                1,
                OBSERVED_AT,
                List.of(order("order-123")),
                List.of(callbackPayment("payment-1", "order-123")),
                List.of(),
                List.of(),
                List.of(),
                List.of(new SimulationScenarioDocument.FailureRecord("PAYMENT", "order-123", "TIMEOUT"))));

        FactQueryException first = assertThrows(FactQueryException.class,
                () -> store.paymentQueryPort().findByOrderId(new OrderId("order-123")));
        FactQueryException second = assertThrows(FactQueryException.class,
                () -> store.paymentQueryPort().findByOrderId(new OrderId("order-123")));

        assertEquals(first.kind(), second.kind());
        assertEquals(first.getMessage(), second.getMessage());
    }

    private static void queryFailingSource(SimulationFactStore store, SimulationFactSource source, OrderId orderId) {
        switch (source) {
            case ORDER -> store.findById(orderId);
            case PAYMENT -> store.findPaymentsByOrderId(orderId);
            case MESSAGE -> store.findMessagesByOrderId(orderId);
            case COMPENSATION -> store.findCompensationsByOrderId(orderId);
            case TRACE -> store.findTraceByOrderId(orderId);
        }
    }

    private static void assertInvalid(String expectedMessage, SimulationScenarioDocument document) {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new SimulationFactStore(document));
        assertEquals(expectedMessage, thrown.getMessage());
    }

    private static SimulationScenarioDocument document(
            List<PaymentTransaction> payments,
            List<MessageDelivery> messages,
            List<CompensationTask> compensations,
            List<TraceSummary> traces,
            List<SimulationScenarioDocument.FailureRecord> failures) {
        return new SimulationScenarioDocument(1, OBSERVED_AT, List.of(order("order-123")), payments, messages, compensations, traces, failures);
    }

    private static OrderSnapshot order(String orderId) {
        return new OrderSnapshot(
                new OrderId(orderId),
                null,
                OrderRole.SINGLE,
                "product-1",
                "Diagnostic Widget",
                "COURSE",
                2,
                new BigDecimal("15.00"),
                new BigDecimal("30.00"),
                new BigDecimal("30.00"),
                "ALIPAY",
                "provider-" + orderId,
                "WEB",
                OrderStatus.PAID,
                ORDERED_AT,
                CALLBACK_RECEIVED_AT,
                ORDERED_AT,
                CALLBACK_RECEIVED_AT);
    }

    private static PaymentTransaction callbackPayment(String transactionId, String orderId) {
        return new PaymentTransaction(
                transactionId,
                new OrderId(orderId),
                "ALIPAY",
                new BigDecimal("30.00"),
                PaymentStatus.CALLBACK_RECEIVED,
                REQUESTED_AT,
                PROVIDER_COMPLETED_AT,
                CALLBACK_RECEIVED_AT,
                null,
                null);
    }

    private static MessageDelivery consumedMessage(String deliveryId, String orderId) {
        return new MessageDelivery(
                deliveryId,
                new OrderId(orderId),
                "OrderPaid",
                "correlation-" + orderId,
                MessageDeliveryStatus.CONSUMED,
                MESSAGE_CREATED_AT,
                SENT_AT,
                CONSUMED_AT,
                null);
    }

    private static CompensationTask succeededCompensation(String taskId, String orderId) {
        return new CompensationTask(
                taskId,
                new OrderId(orderId),
                "RELEASE_STOCK",
                CompensationStatus.SUCCEEDED,
                1,
                3,
                COMPENSATION_CREATED_AT,
                null,
                null);
    }

    private static TraceSummary trace(String traceId, String orderId) {
        return new TraceSummary(
                traceId,
                new OrderId(orderId),
                "correlation-" + orderId,
                REQUESTED_AT,
                CONSUMED_AT,
                true,
                "complete trace");
    }
}
