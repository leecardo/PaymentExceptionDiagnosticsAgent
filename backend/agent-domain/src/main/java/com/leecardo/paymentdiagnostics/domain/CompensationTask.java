package com.leecardo.paymentdiagnostics.domain;

import java.time.Instant;
import java.util.Objects;

public record CompensationTask(
        String taskId,
        OrderId orderId,
        String action,
        CompensationStatus status,
        int retryCount,
        int maxRetries,
        Instant createdAt,
        Instant lastAttemptAt,
        String lastError) {

    public CompensationTask {
        taskId = requireText(taskId, "taskId");
        Objects.requireNonNull(orderId, "orderId must not be null");
        action = requireText(action, "action");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        lastError = normalizeOptionalText(lastError);

        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must not be negative");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
        if (retryCount > maxRetries) {
            throw new IllegalArgumentException("retryCount must not exceed maxRetries");
        }
        if (lastAttemptAt != null && lastAttemptAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("lastAttemptAt must not be before createdAt");
        }
        if (status == CompensationStatus.FAILED && lastError == null) {
            throw new IllegalArgumentException("FAILED compensation tasks must include lastError");
        }
        if (status == CompensationStatus.RETRIES_EXHAUSTED) {
            if (retryCount != maxRetries) {
                throw new IllegalArgumentException("RETRIES_EXHAUSTED compensation tasks must have retryCount equal maxRetries");
            }
            if (lastError == null) {
                throw new IllegalArgumentException("RETRIES_EXHAUSTED compensation tasks must include lastError");
            }
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

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
