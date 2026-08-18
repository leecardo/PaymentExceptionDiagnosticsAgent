package com.leecardo.paymentdiagnostics.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 消息投递记录，表示订单相关领域事件在消息系统中的发送与消费事实。
 * <p>构造时维护创建、发送、消费时间的先后关系，并按 {@link MessageDeliveryStatus}
 * 强制发送时间、消费时间和最近错误字段的合法组合。</p>
 *
 * @param deliveryId 投递记录标识，不能为空白
 * @param orderId 关联订单号
 * @param eventType 订单事件类型，不能为空白
 * @param correlationId 关联链路标识，不能为空白
 * @param status 当前消息投递状态
 * @param createdAt 投递记录创建时间，不能为空
 * @param sentAt 发送到消息系统的时间，发送成功或进入消费阶段时需要
 * @param consumedAt 下游成功消费时间，仅消费成功态需要
 * @param lastError 最近一次发送或消费错误；失败态需要，成功态不允许
 */
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

    /**
     * 规范化消息文本字段，校验发送/消费时间顺序，并按投递状态约束时间戳与错误字段。
     */
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
