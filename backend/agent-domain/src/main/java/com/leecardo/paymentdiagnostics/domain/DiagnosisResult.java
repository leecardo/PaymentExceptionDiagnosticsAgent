package com.leecardo.paymentdiagnostics.domain;

import java.util.List;
import java.util.Objects;

/**
 * Immutable outcome produced by applying one diagnosis rule to an order.
 * <p>诊断结果绑定订单、数据模式、流程阶段、规则标识、摘要、证据和数据质量警告。
 * 除 {@link DiagnosisRuleId#NO_KNOWN_EXCEPTION} 和 {@link DiagnosisRuleId#INSUFFICIENT_EVIDENCE} 外，
 * 其他规则结果必须至少携带一条证据，避免输出无事实支撑的异常结论。</p>
 *
 * @param orderId 被诊断订单号
 * @param dataMode 诊断数据来源模式
 * @param stage 规则识别出的粗粒度支付流程阶段
 * @param ruleId 稳定诊断规则标识
 * @param summary 可读的规则结论摘要，不能为空白
 * @param evidence 支撑结论的来源事实；需要证据的规则不能为空
 * @param warnings 诊断过程产生的非致命数据质量提示，元素不能为空白
 */
public record DiagnosisResult(
        OrderId orderId,
        DataMode dataMode,
        DiagnosisStage stage,
        DiagnosisRuleId ruleId,
        String summary,
        List<DiagnosisEvidence> evidence,
        List<String> warnings) {

    /**
     * 复制证据和警告列表以保持不可变，并校验需要证据的规则不能输出空证据结果。
     */
    public DiagnosisResult {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(dataMode, "dataMode must not be null");
        Objects.requireNonNull(stage, "stage must not be null");
        Objects.requireNonNull(ruleId, "ruleId must not be null");
        summary = requireText(summary, "summary");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
        warnings = normalizeWarnings(warnings);
        if (evidence.isEmpty() && requiresEvidence(ruleId)) {
            throw new IllegalArgumentException("evidence must not be empty for ruleId " + ruleId);
        }
    }

    private static boolean requiresEvidence(DiagnosisRuleId ruleId) {
        return ruleId != DiagnosisRuleId.NO_KNOWN_EXCEPTION
                && ruleId != DiagnosisRuleId.INSUFFICIENT_EVIDENCE;
    }

    private static List<String> normalizeWarnings(List<String> warnings) {
        Objects.requireNonNull(warnings, "warnings must not be null");
        List<String> normalized = warnings.stream()
                .map(warning -> requireText(warning, "warning"))
                .toList();
        return List.copyOf(normalized);
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
