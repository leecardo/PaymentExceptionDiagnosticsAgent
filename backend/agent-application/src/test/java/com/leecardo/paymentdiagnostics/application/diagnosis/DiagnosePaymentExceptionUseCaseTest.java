package com.leecardo.paymentdiagnostics.application.diagnosis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.leecardo.paymentdiagnostics.application.order.OrderNotFoundException;
import com.leecardo.paymentdiagnostics.application.port.CompensationQueryPort;
import com.leecardo.paymentdiagnostics.application.port.FactQueryException;
import com.leecardo.paymentdiagnostics.application.port.MessageQueryPort;
import com.leecardo.paymentdiagnostics.application.port.OrderQueryPort;
import com.leecardo.paymentdiagnostics.application.port.PaymentQueryPort;
import com.leecardo.paymentdiagnostics.application.port.TraceQueryPort;
import com.leecardo.paymentdiagnostics.domain.CompensationStatus;
import com.leecardo.paymentdiagnostics.domain.CompensationTask;
import com.leecardo.paymentdiagnostics.domain.DataMode;
import com.leecardo.paymentdiagnostics.domain.DiagnosisResult;
import com.leecardo.paymentdiagnostics.domain.DiagnosisRuleId;
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

class DiagnosePaymentExceptionUseCaseTest {

    private static final Instant ORDERED_AT = Instant.parse("2026-08-17T10:00:00Z");
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-17T10:05:00Z");
    private static final Instant PROVIDER_COMPLETED_AT = Instant.parse("2026-08-17T10:06:00Z");
    private static final Instant CALLBACK_RECEIVED_AT = Instant.parse("2026-08-17T10:07:00Z");
    private static final Instant MESSAGE_CREATED_AT = Instant.parse("2026-08-17T10:08:00Z");
    private static final Instant SENT_AT = Instant.parse("2026-08-17T10:09:00Z");
    private static final Instant CONSUMED_AT = Instant.parse("2026-08-17T10:10:00Z");
    private static final Instant COMPENSATION_CREATED_AT = Instant.parse("2026-08-17T10:11:00Z");
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-17T10:12:00Z");

    private static final DiagnosisPolicy POLICY = new DiagnosisPolicy(Duration.ofMinutes(10), Duration.ofMinutes(5));
    private static final DataMode DATA_MODE = DataMode.SIMULATION;

    @Test
    void queriesFactsInApprovedOrderAndReturnsRuleDiagnosis() {
        RecordingOrderQueryPort orderPort = new RecordingOrderQueryPort(Optional.of(order("order-123")));
        RecordingPaymentQueryPort paymentPort = new RecordingPaymentQueryPort(List.of(callbackPayment("order-123")));
        RecordingMessageQueryPort messagePort = new RecordingMessageQueryPort(List.of(consumedMessage("order-123")));
        RecordingCompensationQueryPort compensationPort = new RecordingCompensationQueryPort(List.of(succeededCompensation("order-123")));
        RecordingTraceQueryPort tracePort = new RecordingTraceQueryPort(trace("order-123"));
        RecordingClock clock = new RecordingClock(OBSERVED_AT);
        List<String> calls = new ArrayList<>();

        DiagnosisResult result = useCase(orderPort, paymentPort, messagePort, compensationPort, tracePort, clock, calls)
                .diagnose(" order-123 ");

        assertEquals(List.of("order", "payment", "message", "compensation", "trace"), calls);
        assertEquals(DiagnosisRuleId.NO_KNOWN_EXCEPTION, result.ruleId());
        assertEquals(new OrderId("order-123"), result.orderId());
        assertEquals(DATA_MODE, result.dataMode());
        assertEquals(List.of(), result.warnings());
        assertEquals(1, orderPort.queryCount);
        assertEquals(1, paymentPort.queryCount);
        assertEquals(1, messagePort.queryCount);
        assertEquals(1, compensationPort.queryCount);
        assertEquals(1, tracePort.queryCount);
        assertEquals(1, clock.instantCount);
    }

    @Test
    void missingOrderQueriesOnlyOrderAndThrowsOrderNotFoundException() {
        RecordingOrderQueryPort orderPort = new RecordingOrderQueryPort(Optional.empty());
        RecordingPaymentQueryPort paymentPort = new RecordingPaymentQueryPort(List.of(callbackPayment("missing-order")));
        RecordingMessageQueryPort messagePort = new RecordingMessageQueryPort(List.of(consumedMessage("missing-order")));
        RecordingCompensationQueryPort compensationPort = new RecordingCompensationQueryPort(List.of(succeededCompensation("missing-order")));
        RecordingTraceQueryPort tracePort = new RecordingTraceQueryPort(trace("missing-order"));
        RecordingClock clock = new RecordingClock(OBSERVED_AT);
        List<String> calls = new ArrayList<>();

        OrderNotFoundException thrown = assertThrows(OrderNotFoundException.class,
                () -> useCase(orderPort, paymentPort, messagePort, compensationPort, tracePort, clock, calls)
                        .diagnose("missing-order"));

        assertEquals(new OrderId("missing-order"), thrown.orderId());
        assertEquals(List.of("order"), calls);
        assertEquals(1, orderPort.queryCount);
        assertEquals(0, paymentPort.queryCount);
        assertEquals(0, messagePort.queryCount);
        assertEquals(0, compensationPort.queryCount);
        assertEquals(0, tracePort.queryCount);
        assertEquals(0, clock.instantCount);
    }

    @Test
    void invalidOrderIdQueriesNoPorts() {
        RecordingOrderQueryPort orderPort = new RecordingOrderQueryPort(Optional.of(order("order-123")));
        RecordingPaymentQueryPort paymentPort = new RecordingPaymentQueryPort(List.of(callbackPayment("order-123")));
        RecordingMessageQueryPort messagePort = new RecordingMessageQueryPort(List.of(consumedMessage("order-123")));
        RecordingCompensationQueryPort compensationPort = new RecordingCompensationQueryPort(List.of(succeededCompensation("order-123")));
        RecordingTraceQueryPort tracePort = new RecordingTraceQueryPort(trace("order-123"));
        RecordingClock clock = new RecordingClock(OBSERVED_AT);
        List<String> calls = new ArrayList<>();

        assertThrows(IllegalArgumentException.class,
                () -> useCase(orderPort, paymentPort, messagePort, compensationPort, tracePort, clock, calls)
                        .diagnose("not an id"));

        assertEquals(List.of(), calls);
        assertEquals(0, orderPort.queryCount);
        assertEquals(0, paymentPort.queryCount);
        assertEquals(0, messagePort.queryCount);
        assertEquals(0, compensationPort.queryCount);
        assertEquals(0, tracePort.queryCount);
        assertEquals(0, clock.instantCount);
    }

    @Test
    void optionalTraceEmptyStillDelegatesToRules() {
        RecordingClock clock = new RecordingClock(OBSERVED_AT);

        DiagnosisResult result = useCase(
                new RecordingOrderQueryPort(Optional.of(order("order-123"))),
                new RecordingPaymentQueryPort(List.of(callbackPayment("order-123"))),
                new RecordingMessageQueryPort(List.of(consumedMessage("order-123"))),
                new RecordingCompensationQueryPort(List.of(succeededCompensation("order-123"))),
                new RecordingTraceQueryPort(Optional.empty()),
                clock,
                new ArrayList<>()).diagnose("order-123");

        assertEquals(DiagnosisRuleId.TRACE_MISSING, result.ruleId());
        assertEquals(1, clock.instantCount);
    }

    @Test
    void suppliedPortFactsFlowIntoRuleResult() {
        DiagnosisResult result = useCase(
                new RecordingOrderQueryPort(Optional.of(order("order-123"))),
                new RecordingPaymentQueryPort(List.of(failedPayment("order-123"))),
                new RecordingMessageQueryPort(List.of(consumedMessage("order-123"))),
                new RecordingCompensationQueryPort(List.of(succeededCompensation("order-123"))),
                new RecordingTraceQueryPort(trace("order-123")),
                new RecordingClock(OBSERVED_AT),
                new ArrayList<>()).diagnose("order-123");

        assertEquals(DiagnosisRuleId.PAYMENT_FAILED_WITH_PROVIDER_ERROR, result.ruleId());
        assertEquals("payment:payment-failed-order-123", result.evidence().get(0).id());
        assertEquals("payment", result.evidence().get(0).source());
        assertEquals(
                "payment status=FAILED, requestedAt=2026-08-17T10:05:00Z, providerCompletedAt=2026-08-17T10:06:00Z, providerErrorCode=provider_declined, providerErrorSummary=card declined",
                result.evidence().get(0).summary());

        assertEquals(
                DiagnosisRuleId.MESSAGE_SEND_FAILED,
                useCase(
                        new RecordingOrderQueryPort(Optional.of(order("order-123"))),
                        new RecordingPaymentQueryPort(List.of(callbackPayment("order-123"))),
                        new RecordingMessageQueryPort(List.of(sendFailedMessage("order-123"))),
                        new RecordingCompensationQueryPort(List.of(succeededCompensation("order-123"))),
                        new RecordingTraceQueryPort(trace("order-123")),
                        new RecordingClock(OBSERVED_AT),
                        new ArrayList<>()).diagnose("order-123").ruleId());

        assertEquals(
                DiagnosisRuleId.COMPENSATION_RETRIES_EXHAUSTED,
                useCase(
                        new RecordingOrderQueryPort(Optional.of(order("order-123"))),
                        new RecordingPaymentQueryPort(List.of(callbackPayment("order-123"))),
                        new RecordingMessageQueryPort(List.of(consumedMessage("order-123"))),
                        new RecordingCompensationQueryPort(List.of(exhaustedCompensation("order-123"))),
                        new RecordingTraceQueryPort(trace("order-123")),
                        new RecordingClock(OBSERVED_AT),
                        new ArrayList<>()).diagnose("order-123").ruleId());
    }

    @Test
    void factQueryExceptionFromAnyPortPropagatesUnchangedAndSkipsLaterPorts() {
        assertFailureStopsAfter("order", failingOrderPorts(new FactQueryException(FactQueryException.Kind.UNAVAILABLE, "order unavailable")), List.of());
        assertFailureStopsAfter("payment", failingPaymentPorts(new FactQueryException(FactQueryException.Kind.TIMEOUT, "payment timeout")), List.of("order"));
        assertFailureStopsAfter("message", failingMessagePorts(new FactQueryException(FactQueryException.Kind.UNAVAILABLE, "message unavailable")), List.of("order", "payment"));
        assertFailureStopsAfter("compensation", failingCompensationPorts(new FactQueryException(FactQueryException.Kind.TIMEOUT, "compensation timeout")), List.of("order", "payment", "message"));
        assertFailureStopsAfter("trace", failingTracePorts(new FactQueryException(FactQueryException.Kind.UNAVAILABLE, "trace unavailable")), List.of("order", "payment", "message", "compensation"));
    }

    @Test
    void repeatedExecutionWithFixedClockYieldsEqualResultsAndSamplesClockOncePerCall() {
        RecordingClock clock = new RecordingClock(OBSERVED_AT);
        DiagnosePaymentExceptionUseCase useCase = new DiagnosePaymentExceptionUseCase(
                orderId -> Optional.of(order(orderId.value())),
                orderId -> List.of(callbackPayment(orderId.value())),
                orderId -> List.of(consumedMessage(orderId.value())),
                orderId -> List.of(succeededCompensation(orderId.value())),
                orderId -> trace(orderId.value()),
                new DeterministicDiagnosisRules(POLICY),
                clock,
                DATA_MODE);

        DiagnosisResult first = useCase.diagnose("order-123");
        DiagnosisResult second = useCase.diagnose("order-123");

        assertEquals(first, second);
        assertEquals(2, clock.instantCount);
    }

    @Test
    void requiresConstructorDependencies() {
        OrderQueryPort orderPort = orderId -> Optional.of(order(orderId.value()));
        PaymentQueryPort paymentPort = orderId -> List.of(callbackPayment(orderId.value()));
        MessageQueryPort messagePort = orderId -> List.of(consumedMessage(orderId.value()));
        CompensationQueryPort compensationPort = orderId -> List.of(succeededCompensation(orderId.value()));
        TraceQueryPort tracePort = orderId -> trace(orderId.value());
        DeterministicDiagnosisRules rules = new DeterministicDiagnosisRules(POLICY);
        RecordingClock clock = new RecordingClock(OBSERVED_AT);

        assertThrows(NullPointerException.class, () -> new DiagnosePaymentExceptionUseCase(null, paymentPort, messagePort, compensationPort, tracePort, rules, clock, DATA_MODE));
        assertThrows(NullPointerException.class, () -> new DiagnosePaymentExceptionUseCase(orderPort, null, messagePort, compensationPort, tracePort, rules, clock, DATA_MODE));
        assertThrows(NullPointerException.class, () -> new DiagnosePaymentExceptionUseCase(orderPort, paymentPort, null, compensationPort, tracePort, rules, clock, DATA_MODE));
        assertThrows(NullPointerException.class, () -> new DiagnosePaymentExceptionUseCase(orderPort, paymentPort, messagePort, null, tracePort, rules, clock, DATA_MODE));
        assertThrows(NullPointerException.class, () -> new DiagnosePaymentExceptionUseCase(orderPort, paymentPort, messagePort, compensationPort, null, rules, clock, DATA_MODE));
        assertThrows(NullPointerException.class, () -> new DiagnosePaymentExceptionUseCase(orderPort, paymentPort, messagePort, compensationPort, tracePort, null, clock, DATA_MODE));
        assertThrows(NullPointerException.class, () -> new DiagnosePaymentExceptionUseCase(orderPort, paymentPort, messagePort, compensationPort, tracePort, rules, null, DATA_MODE));
        assertThrows(NullPointerException.class, () -> new DiagnosePaymentExceptionUseCase(orderPort, paymentPort, messagePort, compensationPort, tracePort, rules, clock, null));
    }

    @Test
    void rejectsNullPortReturnsDefensively() {
        assertNullReturnRejected("order", orderId -> null, orderId -> List.of(), orderId -> List.of(), orderId -> List.of(), orderId -> Optional.empty());
        assertNullReturnRejected("payment", orderId -> Optional.of(order(orderId.value())), orderId -> null, orderId -> List.of(), orderId -> List.of(), orderId -> Optional.empty());
        assertNullReturnRejected("message", orderId -> Optional.of(order(orderId.value())), orderId -> List.of(), orderId -> null, orderId -> List.of(), orderId -> Optional.empty());
        assertNullReturnRejected("compensation", orderId -> Optional.of(order(orderId.value())), orderId -> List.of(), orderId -> List.of(), orderId -> null, orderId -> Optional.empty());
        assertNullReturnRejected("trace", orderId -> Optional.of(order(orderId.value())), orderId -> List.of(), orderId -> List.of(), orderId -> List.of(), orderId -> null);
    }

    private static void assertFailureStopsAfter(String failingPort, Ports ports, List<String> priorCalls) {
        RecordingClock clock = new RecordingClock(OBSERVED_AT);
        List<String> calls = new ArrayList<>();

        FactQueryException thrown = assertThrows(FactQueryException.class,
                () -> useCase(ports.orderPort, ports.paymentPort, ports.messagePort, ports.compensationPort, ports.tracePort,
                        clock, calls).diagnose("order-123"));

        assertSame(ports.failure, thrown);
        List<String> expectedCalls = new ArrayList<>(priorCalls);
        expectedCalls.add(failingPort);
        assertEquals(expectedCalls, calls);
        assertEquals(0, clock.instantCount);
    }

    private static void assertNullReturnRejected(
            String portName,
            OrderQueryPort orderPort,
            PaymentQueryPort paymentPort,
            MessageQueryPort messagePort,
            CompensationQueryPort compensationPort,
            TraceQueryPort tracePort) {
        NullPointerException thrown = assertThrows(NullPointerException.class,
                () -> useCase(orderPort, paymentPort, messagePort, compensationPort, tracePort,
                        new RecordingClock(OBSERVED_AT), new ArrayList<>()).diagnose("order-123"));

        assertEquals(portName + " query must not return null", thrown.getMessage());
    }

    private static DiagnosePaymentExceptionUseCase useCase(
            OrderQueryPort orderPort,
            PaymentQueryPort paymentPort,
            MessageQueryPort messagePort,
            CompensationQueryPort compensationPort,
            TraceQueryPort tracePort,
            RecordingClock clock,
            List<String> calls) {
        return new DiagnosePaymentExceptionUseCase(
                recordOrder(orderPort, calls),
                recordPayment(paymentPort, calls),
                recordMessage(messagePort, calls),
                recordCompensation(compensationPort, calls),
                recordTrace(tracePort, calls),
                new DeterministicDiagnosisRules(POLICY),
                clock,
                DATA_MODE);
    }

    private static OrderQueryPort recordOrder(OrderQueryPort delegate, List<String> calls) {
        return orderId -> {
            calls.add("order");
            return delegate.findById(orderId);
        };
    }

    private static PaymentQueryPort recordPayment(PaymentQueryPort delegate, List<String> calls) {
        return orderId -> {
            calls.add("payment");
            return delegate.findByOrderId(orderId);
        };
    }

    private static MessageQueryPort recordMessage(MessageQueryPort delegate, List<String> calls) {
        return orderId -> {
            calls.add("message");
            return delegate.findByOrderId(orderId);
        };
    }

    private static CompensationQueryPort recordCompensation(CompensationQueryPort delegate, List<String> calls) {
        return orderId -> {
            calls.add("compensation");
            return delegate.findByOrderId(orderId);
        };
    }

    private static TraceQueryPort recordTrace(TraceQueryPort delegate, List<String> calls) {
        return orderId -> {
            calls.add("trace");
            return delegate.findByOrderId(orderId);
        };
    }

    private static Ports failingOrderPorts(FactQueryException failure) {
        return new Ports(new RecordingOrderQueryPort(failure), orderId -> List.of(), orderId -> List.of(), orderId -> List.of(), orderId -> Optional.empty(), failure);
    }

    private static Ports failingPaymentPorts(FactQueryException failure) {
        return new Ports(orderId -> Optional.of(order(orderId.value())), new RecordingPaymentQueryPort(failure), orderId -> List.of(), orderId -> List.of(), orderId -> Optional.empty(), failure);
    }

    private static Ports failingMessagePorts(FactQueryException failure) {
        return new Ports(orderId -> Optional.of(order(orderId.value())), orderId -> List.of(), new RecordingMessageQueryPort(failure), orderId -> List.of(), orderId -> Optional.empty(), failure);
    }

    private static Ports failingCompensationPorts(FactQueryException failure) {
        return new Ports(orderId -> Optional.of(order(orderId.value())), orderId -> List.of(), orderId -> List.of(), new RecordingCompensationQueryPort(failure), orderId -> Optional.empty(), failure);
    }

    private static Ports failingTracePorts(FactQueryException failure) {
        return new Ports(orderId -> Optional.of(order(orderId.value())), orderId -> List.of(), orderId -> List.of(), orderId -> List.of(), new RecordingTraceQueryPort(failure), failure);
    }

    private static OrderSnapshot order(String orderId) {
        return new OrderSnapshot(
                new OrderId(orderId),
                null,
                OrderRole.SINGLE,
                "product-001",
                "Diagnostic Product",
                "course",
                1,
                new BigDecimal("39.80"),
                new BigDecimal("39.80"),
                new BigDecimal("39.80"),
                "stripe",
                "provider-order-123",
                "web",
                OrderStatus.PAID,
                ORDERED_AT,
                CALLBACK_RECEIVED_AT,
                ORDERED_AT,
                CALLBACK_RECEIVED_AT);
    }

    private static PaymentTransaction callbackPayment(String orderId) {
        return new PaymentTransaction(
                "payment-" + orderId,
                new OrderId(orderId),
                "stripe",
                new BigDecimal("39.80"),
                PaymentStatus.CALLBACK_RECEIVED,
                REQUESTED_AT,
                PROVIDER_COMPLETED_AT,
                CALLBACK_RECEIVED_AT,
                null,
                null);
    }

    private static PaymentTransaction failedPayment(String orderId) {
        return new PaymentTransaction(
                "payment-failed-" + orderId,
                new OrderId(orderId),
                "stripe",
                new BigDecimal("39.80"),
                PaymentStatus.FAILED,
                REQUESTED_AT,
                PROVIDER_COMPLETED_AT,
                null,
                "provider_declined",
                "card declined");
    }

    private static MessageDelivery consumedMessage(String orderId) {
        return new MessageDelivery(
                "message-" + orderId,
                new OrderId(orderId),
                "PaymentCompleted",
                "corr-123",
                MessageDeliveryStatus.CONSUMED,
                MESSAGE_CREATED_AT,
                SENT_AT,
                CONSUMED_AT,
                null);
    }

    private static MessageDelivery sendFailedMessage(String orderId) {
        return new MessageDelivery(
                "message-send-failed-" + orderId,
                new OrderId(orderId),
                "PaymentCompleted",
                "corr-123",
                MessageDeliveryStatus.SEND_FAILED,
                MESSAGE_CREATED_AT,
                null,
                null,
                "broker unavailable");
    }

    private static CompensationTask succeededCompensation(String orderId) {
        return new CompensationTask(
                "compensation-" + orderId,
                new OrderId(orderId),
                "reverse-payment",
                CompensationStatus.SUCCEEDED,
                1,
                3,
                COMPENSATION_CREATED_AT,
                COMPENSATION_CREATED_AT,
                null);
    }

    private static CompensationTask exhaustedCompensation(String orderId) {
        return new CompensationTask(
                "compensation-exhausted-" + orderId,
                new OrderId(orderId),
                "reverse-payment",
                CompensationStatus.RETRIES_EXHAUSTED,
                3,
                3,
                COMPENSATION_CREATED_AT,
                COMPENSATION_CREATED_AT,
                "gateway unavailable");
    }

    private static Optional<TraceSummary> trace(String orderId) {
        return Optional.of(new TraceSummary(
                "trace-" + orderId,
                new OrderId(orderId),
                "corr-123",
                REQUESTED_AT,
                OBSERVED_AT,
                true,
                "complete payment trace"));
    }

    private record Ports(
            OrderQueryPort orderPort,
            PaymentQueryPort paymentPort,
            MessageQueryPort messagePort,
            CompensationQueryPort compensationPort,
            TraceQueryPort tracePort,
            FactQueryException failure) {
    }

    private static final class RecordingOrderQueryPort implements OrderQueryPort {
        private final Optional<OrderSnapshot> result;
        private final FactQueryException failure;
        private int queryCount;

        private RecordingOrderQueryPort(Optional<OrderSnapshot> result) {
            this.result = result;
            this.failure = null;
        }

        private RecordingOrderQueryPort(FactQueryException failure) {
            this.result = Optional.empty();
            this.failure = failure;
        }

        @Override
        public Optional<OrderSnapshot> findById(OrderId orderId) {
            queryCount++;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class RecordingPaymentQueryPort implements PaymentQueryPort {
        private final List<PaymentTransaction> result;
        private final FactQueryException failure;
        private int queryCount;

        private RecordingPaymentQueryPort(List<PaymentTransaction> result) {
            this.result = List.copyOf(result);
            this.failure = null;
        }

        private RecordingPaymentQueryPort(FactQueryException failure) {
            this.result = List.of();
            this.failure = failure;
        }

        @Override
        public List<PaymentTransaction> findByOrderId(OrderId orderId) {
            queryCount++;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class RecordingMessageQueryPort implements MessageQueryPort {
        private final List<MessageDelivery> result;
        private final FactQueryException failure;
        private int queryCount;

        private RecordingMessageQueryPort(List<MessageDelivery> result) {
            this.result = List.copyOf(result);
            this.failure = null;
        }

        private RecordingMessageQueryPort(FactQueryException failure) {
            this.result = List.of();
            this.failure = failure;
        }

        @Override
        public List<MessageDelivery> findByOrderId(OrderId orderId) {
            queryCount++;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class RecordingCompensationQueryPort implements CompensationQueryPort {
        private final List<CompensationTask> result;
        private final FactQueryException failure;
        private int queryCount;

        private RecordingCompensationQueryPort(List<CompensationTask> result) {
            this.result = List.copyOf(result);
            this.failure = null;
        }

        private RecordingCompensationQueryPort(FactQueryException failure) {
            this.result = List.of();
            this.failure = failure;
        }

        @Override
        public List<CompensationTask> findByOrderId(OrderId orderId) {
            queryCount++;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class RecordingTraceQueryPort implements TraceQueryPort {
        private final Optional<TraceSummary> result;
        private final FactQueryException failure;
        private int queryCount;

        private RecordingTraceQueryPort(Optional<TraceSummary> result) {
            this.result = result;
            this.failure = null;
        }

        private RecordingTraceQueryPort(FactQueryException failure) {
            this.result = Optional.empty();
            this.failure = failure;
        }

        @Override
        public Optional<TraceSummary> findByOrderId(OrderId orderId) {
            queryCount++;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    private static final class RecordingClock extends Clock {
        private final Instant instant;
        private int instantCount;

        private RecordingClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            instantCount++;
            return instant;
        }
    }
}
