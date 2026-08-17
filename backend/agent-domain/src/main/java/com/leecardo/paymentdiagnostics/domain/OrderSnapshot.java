package com.leecardo.paymentdiagnostics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of order facts used by payment exception diagnostics.
 */
public record OrderSnapshot(
        OrderId orderId,
        OrderId masterOrderId,
        OrderRole role,
        String productId,
        String productName,
        String productType,
        int goodsCount,
        BigDecimal unitPrice,
        BigDecimal orderAmount,
        BigDecimal paymentAmount,
        String paymentSource,
        String providerOrderId,
        String orderSource,
        OrderStatus status,
        Instant orderedAt,
        Instant stateChangedAt,
        Instant createdAt,
        Instant updatedAt) {

    public OrderSnapshot {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        productId = requireText(productId, "productId");
        productName = requireText(productName, "productName");
        productType = requireText(productType, "productType");
        if (goodsCount <= 0) {
            throw new IllegalArgumentException("goodsCount must be greater than zero");
        }
        requireNonNegative(unitPrice, "unitPrice");
        requireNonNegative(orderAmount, "orderAmount");
        requireNonNegative(paymentAmount, "paymentAmount");
        paymentSource = normalizeOptionalText(paymentSource);
        providerOrderId = normalizeOptionalText(providerOrderId);
        orderSource = requireText(orderSource, "orderSource");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(orderedAt, "orderedAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (role == OrderRole.SUB) {
            if (masterOrderId == null) {
                throw new IllegalArgumentException("SUB orders must have a masterOrderId");
            }
        } else if (masterOrderId != null) {
            throw new IllegalArgumentException("SINGLE and MASTER orders must not have a masterOrderId");
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

    private static void requireNonNegative(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
