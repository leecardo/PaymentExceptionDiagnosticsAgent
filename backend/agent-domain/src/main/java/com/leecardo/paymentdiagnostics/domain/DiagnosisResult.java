package com.leecardo.paymentdiagnostics.domain;

import java.util.List;
import java.util.Objects;

/**
 * Immutable outcome produced by applying one diagnosis rule to an order.
 *
 * @param orderId order being diagnosed
 * @param dataMode source mode used to collect diagnostic data
 * @param stage coarse payment flow stage identified by the rule
 * @param ruleId stable rule identifier
 * @param summary human-readable rule outcome
 * @param evidence source facts supporting the outcome
 * @param warnings non-fatal data quality notes emitted while diagnosing
 */
public record DiagnosisResult(
        OrderId orderId,
        DataMode dataMode,
        DiagnosisStage stage,
        DiagnosisRuleId ruleId,
        String summary,
        List<DiagnosisEvidence> evidence,
        List<String> warnings) {

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
