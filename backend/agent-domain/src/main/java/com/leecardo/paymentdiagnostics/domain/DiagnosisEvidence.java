package com.leecardo.paymentdiagnostics.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable fact collected from a controlled read-only diagnostic source.
 * <p>诊断证据表示一次规则判断引用的只读事实，例如订单库记录、支付流水、消息状态或链路观测。
 * 证据只描述已观察到的事实，不承载推断结论。</p>
 *
 * @param id 诊断结果内稳定的证据标识，不能为空白
 * @param source 产生该观测的工具或系统来源，不能为空白
 * @param summary 可读的事实摘要，不能为空白且不编造结论
 * @param observedAt 事实被观察或记录的时间，不能为空
 */
public record DiagnosisEvidence(String id, String source, String summary, Instant observedAt) {

    /**
     * 规范化证据文本字段，并要求观察时间非空。
     */
    public DiagnosisEvidence {
        id = requireText(id, "id");
        source = requireText(source, "source");
        summary = requireText(summary, "summary");
        Objects.requireNonNull(observedAt, "observedAt must not be null");
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
