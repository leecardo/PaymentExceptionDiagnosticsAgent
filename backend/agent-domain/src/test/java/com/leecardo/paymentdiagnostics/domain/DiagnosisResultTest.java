package com.leecardo.paymentdiagnostics.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class DiagnosisResultTest {

    private static final OrderId ORDER_ID = new OrderId("order-123");
    private static final DiagnosisEvidence EVIDENCE = new DiagnosisEvidence(
            "payment-log-1",
            "payment-service",
            "provider callback received",
            Instant.parse("2026-08-14T10:15:30Z"));

    @Test
    void requiresCoreFieldsAndLists() {
        assertThrows(NullPointerException.class, () -> result(null, DataMode.SIMULATION, DiagnosisStage.PAYMENT_CALLBACK,
                DiagnosisRuleId.PROVIDER_SUCCEEDED_CALLBACK_MISSING, "summary", List.of(EVIDENCE), List.of()));
        assertThrows(NullPointerException.class, () -> result(ORDER_ID, null, DiagnosisStage.PAYMENT_CALLBACK,
                DiagnosisRuleId.PROVIDER_SUCCEEDED_CALLBACK_MISSING, "summary", List.of(EVIDENCE), List.of()));
        assertThrows(NullPointerException.class, () -> result(ORDER_ID, DataMode.SIMULATION, null,
                DiagnosisRuleId.PROVIDER_SUCCEEDED_CALLBACK_MISSING, "summary", List.of(EVIDENCE), List.of()));
        assertThrows(NullPointerException.class, () -> result(ORDER_ID, DataMode.SIMULATION, DiagnosisStage.PAYMENT_CALLBACK,
                null, "summary", List.of(EVIDENCE), List.of()));
        assertThrows(NullPointerException.class, () -> result(ORDER_ID, DataMode.SIMULATION, DiagnosisStage.PAYMENT_CALLBACK,
                DiagnosisRuleId.PROVIDER_SUCCEEDED_CALLBACK_MISSING, null, List.of(EVIDENCE), List.of()));
        assertThrows(NullPointerException.class, () -> result(ORDER_ID, DataMode.SIMULATION, DiagnosisStage.PAYMENT_CALLBACK,
                DiagnosisRuleId.PROVIDER_SUCCEEDED_CALLBACK_MISSING, "summary", null, List.of()));
        assertThrows(NullPointerException.class, () -> result(ORDER_ID, DataMode.SIMULATION, DiagnosisStage.PAYMENT_CALLBACK,
                DiagnosisRuleId.PROVIDER_SUCCEEDED_CALLBACK_MISSING, "summary", List.of(EVIDENCE), null));
        assertThrows(IllegalArgumentException.class, () -> result(ORDER_ID, DataMode.SIMULATION, DiagnosisStage.PAYMENT_CALLBACK,
                DiagnosisRuleId.PROVIDER_SUCCEEDED_CALLBACK_MISSING, " ", List.of(EVIDENCE), List.of()));
    }

    @Test
    void normalizesSummaryAndWarnings() {
        DiagnosisResult result = result(
                ORDER_ID,
                DataMode.POSTGRES,
                DiagnosisStage.MESSAGE_DELIVERY,
                DiagnosisRuleId.MESSAGE_SEND_FAILED,
                " message send failed ",
                List.of(EVIDENCE),
                List.of(" first warning ", "\tsecond warning\n"));

        assertEquals("message send failed", result.summary());
        assertEquals(List.of("first warning", "second warning"), result.warnings());
    }

    @Test
    void copiesEvidenceAndWarningsWithoutMutableExposure() {
        List<DiagnosisEvidence> evidence = new ArrayList<>();
        evidence.add(EVIDENCE);
        List<String> warnings = new ArrayList<>();
        warnings.add("check replay lag");

        DiagnosisResult result = result(ORDER_ID, DataMode.SIMULATION, DiagnosisStage.MESSAGE_DELIVERY,
                DiagnosisRuleId.MESSAGE_SEND_FAILED, "message send failed", evidence, warnings);
        evidence.clear();
        warnings.set(0, "mutated");

        assertEquals(List.of(EVIDENCE), result.evidence());
        assertEquals(List.of("check replay lag"), result.warnings());
        assertThrows(UnsupportedOperationException.class, () -> result.evidence().add(EVIDENCE));
        assertThrows(UnsupportedOperationException.class, () -> result.warnings().add("another warning"));
    }

    @Test
    void rejectsNullEvidenceAndNullOrBlankWarnings() {
        assertThrows(NullPointerException.class, () -> result(ORDER_ID, DataMode.SIMULATION, DiagnosisStage.MESSAGE_DELIVERY,
                DiagnosisRuleId.MESSAGE_SEND_FAILED, "message send failed", listWithNullEvidence(), List.of()));
        assertThrows(NullPointerException.class, () -> result(ORDER_ID, DataMode.SIMULATION, DiagnosisStage.MESSAGE_DELIVERY,
                DiagnosisRuleId.MESSAGE_SEND_FAILED, "message send failed", List.of(EVIDENCE), listWithNullWarning()));
        assertThrows(IllegalArgumentException.class, () -> result(ORDER_ID, DataMode.SIMULATION, DiagnosisStage.MESSAGE_DELIVERY,
                DiagnosisRuleId.MESSAGE_SEND_FAILED, "message send failed", List.of(EVIDENCE), List.of(" ")));
    }

    @Test
    void requiresEvidenceForEveryRuleExceptNoKnownExceptionAndInsufficientEvidence() {
        for (DiagnosisRuleId ruleId : DiagnosisRuleId.values()) {
            if (ruleId == DiagnosisRuleId.NO_KNOWN_EXCEPTION || ruleId == DiagnosisRuleId.INSUFFICIENT_EVIDENCE) {
                DiagnosisResult result = result(ORDER_ID, DataMode.SIMULATION, DiagnosisStage.INSUFFICIENT_EVIDENCE,
                        ruleId, "optional evidence", List.of(), List.of());

                assertEquals(List.of(), result.evidence());
            } else {
                assertThrows(IllegalArgumentException.class, () -> result(ORDER_ID, DataMode.SIMULATION,
                        DiagnosisStage.MESSAGE_DELIVERY, ruleId, "evidence required", List.of(), List.of()));
            }
        }
    }

    @Test
    void exposesExactRuleIdsDataModesAndStages() {
        assertArrayEquals(new DiagnosisRuleId[] {
                DiagnosisRuleId.NO_KNOWN_EXCEPTION,
                DiagnosisRuleId.PAYMENT_NOT_STARTED,
                DiagnosisRuleId.PAYMENT_PROCESSING_TIMEOUT,
                DiagnosisRuleId.PROVIDER_SUCCEEDED_CALLBACK_MISSING,
                DiagnosisRuleId.CALLBACK_SUCCEEDED_ORDER_NOT_UPDATED,
                DiagnosisRuleId.PAYMENT_FAILED_WITH_PROVIDER_ERROR,
                DiagnosisRuleId.MESSAGE_NOT_SENT,
                DiagnosisRuleId.MESSAGE_SEND_FAILED,
                DiagnosisRuleId.MESSAGE_NOT_CONSUMED,
                DiagnosisRuleId.MESSAGE_CONSUME_FAILED,
                DiagnosisRuleId.COMPENSATION_NOT_CREATED,
                DiagnosisRuleId.COMPENSATION_FAILED,
                DiagnosisRuleId.COMPENSATION_RETRIES_EXHAUSTED,
                DiagnosisRuleId.TRACE_MISSING,
                DiagnosisRuleId.INSUFFICIENT_EVIDENCE
        }, DiagnosisRuleId.values());
        assertArrayEquals(new DataMode[] { DataMode.SIMULATION, DataMode.POSTGRES }, DataMode.values());
        assertArrayEquals(new DiagnosisStage[] {
                DiagnosisStage.ORDER_CREATED,
                DiagnosisStage.PAYMENT_REQUESTED,
                DiagnosisStage.PAYMENT_CONFIRMED,
                DiagnosisStage.PAYMENT_CALLBACK,
                DiagnosisStage.ORDER_STATE_UPDATE,
                DiagnosisStage.MESSAGE_DELIVERY,
                DiagnosisStage.COMPENSATION,
                DiagnosisStage.TRACE_CORRELATION,
                DiagnosisStage.COMPLETED,
                DiagnosisStage.INSUFFICIENT_EVIDENCE
        }, DiagnosisStage.values());
    }

    private static DiagnosisResult result(
            OrderId orderId,
            DataMode dataMode,
            DiagnosisStage stage,
            DiagnosisRuleId ruleId,
            String summary,
            List<DiagnosisEvidence> evidence,
            List<String> warnings) {
        return new DiagnosisResult(orderId, dataMode, stage, ruleId, summary, evidence, warnings);
    }

    private static List<DiagnosisEvidence> listWithNullEvidence() {
        List<DiagnosisEvidence> evidence = new ArrayList<>();
        evidence.add(null);
        return evidence;
    }

    private static List<String> listWithNullWarning() {
        List<String> warnings = new ArrayList<>();
        warnings.add(null);
        return warnings;
    }
}
