package com.leecardo.paymentdiagnostics.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * 调用链路摘要，汇总一次订单支付诊断所需的 trace/correlation 观测结果。
 * <p>构造时要求链路标识、订单号、关联标识、开始时间和摘要非空，并保证结束时间不早于开始时间。</p>
 *
 * @param traceId 调用链路标识，不能为空白
 * @param orderId 关联订单号
 * @param correlationId 业务关联标识，不能为空白
 * @param startedAt 链路开始时间，不能为空
 * @param endedAt 链路结束时间，可为空；存在时不能早于开始时间
 * @param complete 链路是否完整覆盖诊断所需关键节点
 * @param summary 链路摘要说明，不能为空白
 */
public record TraceSummary(
        String traceId,
        OrderId orderId,
        String correlationId,
        Instant startedAt,
        Instant endedAt,
        boolean complete,
        String summary) {

    /**
     * 规范化链路文本字段，并校验结束时间不能早于开始时间。
     */
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
