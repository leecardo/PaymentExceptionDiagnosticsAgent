package com.leecardo.paymentdiagnostics.application.diagnosis;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.leecardo.paymentdiagnostics.domain.CompensationTask;
import com.leecardo.paymentdiagnostics.domain.DataMode;
import com.leecardo.paymentdiagnostics.domain.MessageDelivery;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;
import com.leecardo.paymentdiagnostics.domain.PaymentTransaction;
import com.leecardo.paymentdiagnostics.domain.TraceSummary;

public record CollectedFacts(
        OrderSnapshot order,
        List<PaymentTransaction> payments,
        List<MessageDelivery> messages,
        List<CompensationTask> compensations,
        Optional<TraceSummary> trace,
        Instant observedAt,
        DataMode dataMode,
        List<String> warnings) {

    public CollectedFacts {
        Objects.requireNonNull(order, "order must not be null");
        payments = List.copyOf(Objects.requireNonNull(payments, "payments must not be null"));
        messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
        compensations = List.copyOf(Objects.requireNonNull(compensations, "compensations must not be null"));
        trace = Objects.requireNonNull(trace, "trace must not be null");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        Objects.requireNonNull(dataMode, "dataMode must not be null");
        warnings = normalizeWarnings(warnings);
    }

    private static List<String> normalizeWarnings(List<String> warnings) {
        Objects.requireNonNull(warnings, "warnings must not be null");
        return List.copyOf(warnings.stream()
                .map(warning -> requireText(warning, "warning"))
                .toList());
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
