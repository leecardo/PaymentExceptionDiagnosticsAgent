package com.leecardo.paymentdiagnostics.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class DiagnosisEvidenceTest {

    @Test
    void rejectsBlankSource() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosisEvidence(" ", "payment captured", Instant.parse("2026-08-14T10:15:30Z")));
    }

    @Test
    void rejectsBlankSummary() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosisEvidence("payment-service", "\t", Instant.parse("2026-08-14T10:15:30Z")));
    }

    @Test
    void rejectsMissingObservationTime() {
        assertThrows(NullPointerException.class,
                () -> new DiagnosisEvidence("payment-service", "payment captured", null));
    }

    @Test
    void trimsSourceAndSummary() {
        DiagnosisEvidence evidence = new DiagnosisEvidence(
                " payment-service ",
                " payment captured ",
                Instant.parse("2026-08-14T10:15:30Z"));

        assertEquals("payment-service", evidence.source());
        assertEquals("payment captured", evidence.summary());
    }
}
