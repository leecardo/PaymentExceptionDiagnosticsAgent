package com.leecardo.paymentdiagnostics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record PaymentTransaction(
        String transactionId,
        OrderId orderId,
        String provider,
        BigDecimal amount,
        PaymentStatus status,
        Instant requestedAt,
        Instant providerCompletedAt,
        Instant callbackReceivedAt,
        String providerErrorCode,
        String providerErrorSummary) {

    public PaymentTransaction {
        transactionId = requireText(transactionId, "transactionId");
        Objects.requireNonNull(orderId, "orderId must not be null");
        provider = requireText(provider, "provider");
        requireNonNegative(amount, "amount");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        providerErrorCode = normalizeOptionalText(providerErrorCode);
        providerErrorSummary = normalizeOptionalText(providerErrorSummary);

        if (providerCompletedAt != null && providerCompletedAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("providerCompletedAt must not be before requestedAt");
        }
        if (callbackReceivedAt != null && callbackReceivedAt.isBefore(requestedAt)) {
            throw new IllegalArgumentException("callbackReceivedAt must not be before requestedAt");
        }
        if (providerCompletedAt != null && callbackReceivedAt != null && callbackReceivedAt.isBefore(providerCompletedAt)) {
            throw new IllegalArgumentException("callbackReceivedAt must not be before providerCompletedAt");
        }
        switch (status) {
            case REQUESTED, PROCESSING -> {
                if (providerCompletedAt != null || callbackReceivedAt != null) {
                    throw new IllegalArgumentException(status + " payments must not include providerCompletedAt or callbackReceivedAt");
                }
                rejectProviderError(providerErrorCode, providerErrorSummary, status);
            }
            case PROVIDER_SUCCEEDED -> {
                if (providerCompletedAt == null || callbackReceivedAt != null) {
                    throw new IllegalArgumentException("PROVIDER_SUCCEEDED payments require providerCompletedAt only");
                }
                rejectProviderError(providerErrorCode, providerErrorSummary, status);
            }
            case CALLBACK_RECEIVED -> {
                if (callbackReceivedAt == null) {
                    throw new IllegalArgumentException("CALLBACK_RECEIVED payments require callbackReceivedAt");
                }
                rejectProviderError(providerErrorCode, providerErrorSummary, status);
            }
            case FAILED -> {
                if (providerErrorCode == null || providerErrorSummary == null) {
                    throw new IllegalArgumentException("FAILED payments must include providerErrorCode and providerErrorSummary");
                }
            }
        }
    }

    private static void rejectProviderError(String providerErrorCode, String providerErrorSummary, PaymentStatus status) {
        if (providerErrorCode != null || providerErrorSummary != null) {
            throw new IllegalArgumentException(status + " payments must not include provider error fields");
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
