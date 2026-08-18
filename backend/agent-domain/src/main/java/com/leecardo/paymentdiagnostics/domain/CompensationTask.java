package com.leecardo.paymentdiagnostics.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 补偿任务记录，表示针对支付异常订单创建的修复、重推或同步动作。
 * <p>构造时维护重试次数非负且不能超过最大重试次数、最后尝试时间不能早于创建时间，
 * 并要求失败态和重试耗尽态携带最后错误；重试耗尽态还必须满足 {@code retryCount == maxRetries}。</p>
 *
 * @param taskId 补偿任务标识，不能为空白
 * @param orderId 关联订单号
 * @param action 补偿动作名称或类型，不能为空白
 * @param status 当前补偿任务状态
 * @param retryCount 已重试次数，必须非负且不超过最大重试次数
 * @param maxRetries 最大允许重试次数，必须非负
 * @param createdAt 补偿任务创建时间，不能为空
 * @param lastAttemptAt 最近一次尝试时间，可为空；不能早于创建时间
 * @param lastError 最近一次失败错误；失败态和重试耗尽态必填
 */
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

    /**
     * 规范化补偿任务文本，校验重试计数和时间线，并强制失败类状态携带错误详情。
     */
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
