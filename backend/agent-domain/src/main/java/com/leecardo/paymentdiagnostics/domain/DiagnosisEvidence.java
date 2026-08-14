package com.leecardo.paymentdiagnostics.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable fact collected from a controlled read-only diagnostic source.
 *
 * @param source tool or system that produced the observation
 * @param summary human-readable observation without fabricated conclusions
 * @param observedAt time the source fact was observed or recorded
 */
public record DiagnosisEvidence(String source, String summary, Instant observedAt) {

    public DiagnosisEvidence {
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
