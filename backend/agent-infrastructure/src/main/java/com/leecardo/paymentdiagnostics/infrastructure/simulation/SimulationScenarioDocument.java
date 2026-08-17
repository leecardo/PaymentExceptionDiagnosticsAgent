package com.leecardo.paymentdiagnostics.infrastructure.simulation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.leecardo.paymentdiagnostics.application.port.FactQueryException;
import com.leecardo.paymentdiagnostics.domain.CompensationTask;
import com.leecardo.paymentdiagnostics.domain.MessageDelivery;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;
import com.leecardo.paymentdiagnostics.domain.PaymentTransaction;
import com.leecardo.paymentdiagnostics.domain.TraceSummary;

public record SimulationScenarioDocument(
        int schemaVersion,
        Instant observedAt,
        List<OrderSnapshot> orders,
        List<PaymentTransaction> paymentTransactions,
        List<MessageDelivery> messageDeliveries,
        List<CompensationTask> compensationTasks,
        List<TraceSummary> traceSummaries,
        List<FailureRecord> failures) {

    public SimulationScenarioDocument {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("schemaVersion must be 1");
        }
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        orders = copyRequired(orders, "orders");
        paymentTransactions = copyRequired(paymentTransactions, "paymentTransactions");
        messageDeliveries = copyRequired(messageDeliveries, "messageDeliveries");
        compensationTasks = copyRequired(compensationTasks, "compensationTasks");
        traceSummaries = copyRequired(traceSummaries, "traceSummaries");
        failures = copyRequired(failures, "failures");
    }

    private static <T> List<T> copyRequired(List<T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        return List.copyOf(values);
    }

    public record FailureRecord(SimulationFactSource source, String orderId, FactQueryException.Kind kind) {

        public FailureRecord(String source, String orderId, String kind) {
            this(parseEnum(SimulationFactSource.class, source, "source"), orderId, parseEnum(FactQueryException.Kind.class, kind, "kind"));
        }

        public FailureRecord {
            Objects.requireNonNull(source, "source must not be null");
            Objects.requireNonNull(orderId, "orderId must not be null");
            orderId = orderId.strip();
            if (orderId.isEmpty()) {
                throw new IllegalArgumentException("orderId must not be blank");
            }
            Objects.requireNonNull(kind, "kind must not be null");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        try {
            return Enum.valueOf(enumType, value.strip());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + " must be one of " + List.of(enumType.getEnumConstants()), ex);
        }
    }
}
