package com.leecardo.paymentdiagnostics.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class DiagnosisEvidenceTest {

    @Test
    void rejectsBlankId() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosisEvidence(" ", "payment-service", "payment captured", Instant.parse("2026-08-14T10:15:30Z")));
    }

    @Test
    void rejectsBlankSource() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosisEvidence("payment-log-1", " ", "payment captured", Instant.parse("2026-08-14T10:15:30Z")));
    }

    @Test
    void rejectsBlankSummary() {
        assertThrows(IllegalArgumentException.class,
                () -> new DiagnosisEvidence("payment-log-1", "payment-service", "\t", Instant.parse("2026-08-14T10:15:30Z")));
    }

    @Test
    void rejectsMissingObservationTime() {
        assertThrows(NullPointerException.class,
                () -> new DiagnosisEvidence("payment-log-1", "payment-service", "payment captured", null));
    }

    @Test
    void trimsIdSourceAndSummary() {
        DiagnosisEvidence evidence = new DiagnosisEvidence(
                " payment-log-1 ",
                " payment-service ",
                " payment captured ",
                Instant.parse("2026-08-14T10:15:30Z"));

        assertEquals("payment-log-1", evidence.id());
        assertEquals("payment-service", evidence.source());
        assertEquals("payment captured", evidence.summary());
    }
}
