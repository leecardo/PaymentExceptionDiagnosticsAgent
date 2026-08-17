package com.leecardo.paymentdiagnostics.infrastructure.simulation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.leecardo.paymentdiagnostics.application.port.CompensationQueryPort;
import com.leecardo.paymentdiagnostics.application.port.FactQueryException;
import com.leecardo.paymentdiagnostics.application.port.MessageQueryPort;
import com.leecardo.paymentdiagnostics.application.port.OrderQueryPort;
import com.leecardo.paymentdiagnostics.application.port.PaymentQueryPort;
import com.leecardo.paymentdiagnostics.application.port.TraceQueryPort;
import com.leecardo.paymentdiagnostics.domain.CompensationTask;
import com.leecardo.paymentdiagnostics.domain.MessageDelivery;
import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;
import com.leecardo.paymentdiagnostics.domain.PaymentTransaction;
import com.leecardo.paymentdiagnostics.domain.TraceSummary;

public final class SimulationFactStore implements OrderQueryPort {

    private static final List<?> EMPTY_LIST = List.of();

    private final Map<OrderId, OrderSnapshot> ordersById;
    private final Map<OrderId, List<PaymentTransaction>> paymentsByOrderId;
    private final Map<OrderId, List<MessageDelivery>> messagesByOrderId;
    private final Map<OrderId, List<CompensationTask>> compensationsByOrderId;
    private final Map<OrderId, TraceSummary> tracesByOrderId;
    private final Instant observedAt;
    private final PaymentQueryPort paymentQueryPort;
    private final MessageQueryPort messageQueryPort;
    private final CompensationQueryPort compensationQueryPort;
    private final TraceQueryPort traceQueryPort;
    private final Map<SimulationFactSource, Map<OrderId, FactQueryException.Kind>> failuresBySourceAndOrderId;

    public SimulationFactStore(SimulationScenarioDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        observedAt = document.observedAt();
        ordersById = ordersById(document.orders());
        Set<OrderId> knownOrderIds = ordersById.keySet();
        paymentsByOrderId = groupedByOrderId(document.paymentTransactions(), knownOrderIds, PaymentTransaction::transactionId, PaymentTransaction::orderId, "transactionId", "payment");
        messagesByOrderId = groupedByOrderId(document.messageDeliveries(), knownOrderIds, MessageDelivery::deliveryId, MessageDelivery::orderId, "deliveryId", "message");
        compensationsByOrderId = groupedByOrderId(document.compensationTasks(), knownOrderIds, CompensationTask::taskId, CompensationTask::orderId, "taskId", "compensation");
        tracesByOrderId = tracesByOrderId(document.traceSummaries(), knownOrderIds);
        failuresBySourceAndOrderId = failuresBySourceAndOrderId(document.failures(), knownOrderIds);
        paymentQueryPort = this::findPaymentsByOrderId;
        messageQueryPort = this::findMessagesByOrderId;
        compensationQueryPort = this::findCompensationsByOrderId;
        traceQueryPort = this::findTraceByOrderId;
    }

    public Instant observedAt() {
        return observedAt;
    }

    public OrderQueryPort orderQueryPort() {
        return this;
    }

    public PaymentQueryPort paymentQueryPort() {
        return paymentQueryPort;
    }

    public MessageQueryPort messageQueryPort() {
        return messageQueryPort;
    }

    public CompensationQueryPort compensationQueryPort() {
        return compensationQueryPort;
    }

    public TraceQueryPort traceQueryPort() {
        return traceQueryPort;
    }

    @Override
    public Optional<OrderSnapshot> findById(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        throwIfConfiguredFailure(SimulationFactSource.ORDER, orderId);
        return Optional.ofNullable(ordersById.get(orderId));
    }

    public List<PaymentTransaction> findPaymentsByOrderId(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        throwIfConfiguredFailure(SimulationFactSource.PAYMENT, orderId);
        return listOrEmpty(paymentsByOrderId.get(orderId));
    }

    public List<MessageDelivery> findMessagesByOrderId(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        throwIfConfiguredFailure(SimulationFactSource.MESSAGE, orderId);
        return listOrEmpty(messagesByOrderId.get(orderId));
    }

    public List<CompensationTask> findCompensationsByOrderId(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        throwIfConfiguredFailure(SimulationFactSource.COMPENSATION, orderId);
        return listOrEmpty(compensationsByOrderId.get(orderId));
    }

    public Optional<TraceSummary> findTraceByOrderId(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        throwIfConfiguredFailure(SimulationFactSource.TRACE, orderId);
        return Optional.ofNullable(tracesByOrderId.get(orderId));
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> listOrEmpty(List<T> values) {
        if (values == null) {
            return (List<T>) EMPTY_LIST;
        }
        return values;
    }

    private void throwIfConfiguredFailure(SimulationFactSource source, OrderId orderId) {
        FactQueryException.Kind kind = failuresBySourceAndOrderId.getOrDefault(source, Map.of()).get(orderId);
        if (kind != null) {
            throw new FactQueryException(kind, source + " facts for orderId " + orderId.value() + " are " + kind);
        }
    }

    private static Map<OrderId, OrderSnapshot> ordersById(List<OrderSnapshot> orders) {
        Map<OrderId, OrderSnapshot> result = new LinkedHashMap<>();
        for (OrderSnapshot order : orders) {
            OrderSnapshot previous = result.putIfAbsent(order.orderId(), order);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate orderId " + order.orderId().value());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<OrderId, TraceSummary> tracesByOrderId(List<TraceSummary> traces, Set<OrderId> knownOrderIds) {
        Set<String> traceIds = new HashSet<>();
        Map<OrderId, TraceSummary> result = new HashMap<>();
        for (TraceSummary trace : traces) {
            if (!traceIds.add(trace.traceId())) {
                throw new IllegalArgumentException("duplicate traceId " + trace.traceId());
            }
            requireKnownOrderId(knownOrderIds, trace.orderId(), "trace " + trace.traceId());
            TraceSummary previous = result.putIfAbsent(trace.orderId(), trace);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate trace for orderId " + trace.orderId().value());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<SimulationFactSource, Map<OrderId, FactQueryException.Kind>> failuresBySourceAndOrderId(
            List<SimulationScenarioDocument.FailureRecord> failures,
            Set<OrderId> knownOrderIds) {
        Map<SimulationFactSource, Map<OrderId, FactQueryException.Kind>> result = new EnumMap<>(SimulationFactSource.class);
        for (SimulationScenarioDocument.FailureRecord failure : failures) {
            OrderId orderId = new OrderId(failure.orderId());
            requireKnownOrderId(knownOrderIds, orderId, "failure");
            Map<OrderId, FactQueryException.Kind> sourceFailures = result.computeIfAbsent(failure.source(), ignored -> new HashMap<>());
            FactQueryException.Kind previous = sourceFailures.putIfAbsent(orderId, failure.kind());
            if (previous != null) {
                throw new IllegalArgumentException("duplicate failure for " + failure.source() + "/" + orderId.value());
            }
        }
        result.replaceAll((source, sourceFailures) -> Map.copyOf(sourceFailures));
        return Map.copyOf(result);
    }

    private static <T> Map<OrderId, List<T>> groupedByOrderId(
            List<T> facts,
            Set<OrderId> knownOrderIds,
            IdExtractor<T> idExtractor,
            OrderIdExtractor<T> orderIdExtractor,
            String idName,
            String factName) {
        Set<String> ids = new HashSet<>();
        Map<OrderId, List<T>> mutable = new LinkedHashMap<>();
        for (T fact : facts) {
            String id = idExtractor.id(fact);
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate " + idName + " " + id);
            }
            OrderId orderId = orderIdExtractor.orderId(fact);
            requireKnownOrderId(knownOrderIds, orderId, factName + " " + id);
            mutable.computeIfAbsent(orderId, ignored -> new ArrayList<>()).add(fact);
        }
        Map<OrderId, List<T>> result = new LinkedHashMap<>();
        mutable.forEach((orderId, values) -> result.put(orderId, List.copyOf(values)));
        return Map.copyOf(result);
    }

    private static void requireKnownOrderId(Set<OrderId> knownOrderIds, OrderId orderId, String owner) {
        if (!knownOrderIds.contains(orderId)) {
            throw new IllegalArgumentException(owner + " references unknown orderId " + orderId.value());
        }
    }

    @FunctionalInterface
    private interface IdExtractor<T> {
        String id(T value);
    }

    @FunctionalInterface
    private interface OrderIdExtractor<T> {
        OrderId orderId(T value);
    }
}
