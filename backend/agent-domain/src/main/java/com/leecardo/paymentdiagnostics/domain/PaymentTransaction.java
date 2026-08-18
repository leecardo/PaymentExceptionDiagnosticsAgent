package com.leecardo.paymentdiagnostics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * 支付流水记录，表示一次订单支付在支付渠道和本系统回调中的状态事实。
 * <p>构造时维护金额非负、请求时间必填、渠道完成时间和回调时间不能早于请求时间，
 * 以及不同 {@link PaymentStatus} 对时间戳和错误字段的约束；失败流水必须携带错误码和错误摘要。</p>
 *
 * @param transactionId 支付流水号，不能为空白
 * @param orderId 所属订单号
 * @param provider 支付渠道或服务商标识，不能为空白
 * @param amount 支付金额，必须非负
 * @param status 支付流水当前状态
 * @param requestedAt 支付请求发起时间，不能为空
 * @param providerCompletedAt 支付渠道完成时间，仅渠道成功态需要
 * @param callbackReceivedAt 本系统收到支付回调时间，回调已收到态需要
 * @param providerErrorCode 渠道错误码，仅失败态允许并且必填
 * @param providerErrorSummary 渠道错误摘要，仅失败态允许并且必填
 */
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

    /**
     * 规范化流水文本，校验金额和时间线，并按支付状态强制时间戳与错误字段组合合法。
     */
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
