package com.leecardo.paymentdiagnostics.domain;

import java.time.Instant;
import java.util.Objects;

public record MessageDelivery(
        String deliveryId,
        OrderId orderId,
        String eventType,
        String correlationId,
        MessageDeliveryStatus status,
        Instant createdAt,
        Instant sentAt,
        Instant consumedAt,
        String lastError) {

    public MessageDelivery {
        deliveryId = requireText(deliveryId, "deliveryId");
        Objects.requireNonNull(orderId, "orderId must not be null");
        eventType = requireText(eventType, "eventType");
        correlationId = requireText(correlationId, "correlationId");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        lastError = normalizeOptionalText(lastError);

        if (sentAt != null && sentAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("sentAt must not be before createdAt");
        }
        if (consumedAt != null && sentAt == null) {
            throw new IllegalArgumentException("consumedAt requires sentAt");
        }
        if (consumedAt != null && consumedAt.isBefore(sentAt)) {
            throw new IllegalArgumentException("consumedAt must not be before sentAt");
        }

        switch (status) {
            case PENDING -> {
                if (sentAt != null || consumedAt != null || lastError != null) {
                    throw new IllegalArgumentException("PENDING deliveries must not include sentAt, consumedAt, or lastError");
                }
            }
            case SENT -> {
                if (sentAt == null || consumedAt != null || lastError != null) {
                    throw new IllegalArgumentException("SENT deliveries require sentAt only");
                }
            }
            case SEND_FAILED -> {
                if (sentAt != null || consumedAt != null || lastError == null) {
                    throw new IllegalArgumentException("SEND_FAILED deliveries require lastError only");
                }
            }
            case CONSUMED -> {
                if (sentAt == null || consumedAt == null || lastError != null) {
                    throw new IllegalArgumentException("CONSUMED deliveries require sentAt and consumedAt without lastError");
                }
            }
            case CONSUME_FAILED -> {
                if (sentAt == null || consumedAt != null || lastError == null) {
                    throw new IllegalArgumentException("CONSUME_FAILED deliveries require sentAt and lastError without consumedAt");
                }
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
