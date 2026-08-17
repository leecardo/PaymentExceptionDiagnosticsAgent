package com.leecardo.paymentdiagnostics.domain;

import java.time.Instant;
import java.util.Objects;

public record TraceSummary(
        String traceId,
        OrderId orderId,
        String correlationId,
        Instant startedAt,
        Instant endedAt,
        boolean complete,
        String summary) {

    public TraceSummary {
        traceId = requireText(traceId, "traceId");
        Objects.requireNonNull(orderId, "orderId must not be null");
        correlationId = requireText(correlationId, "correlationId");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        summary = requireText(summary, "summary");

        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("endedAt must not be before startedAt");
        }
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
