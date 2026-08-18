package com.leecardo.paymentdiagnostics.application.diagnosis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.leecardo.paymentdiagnostics.domain.CompensationStatus;
import com.leecardo.paymentdiagnostics.domain.CompensationTask;
import com.leecardo.paymentdiagnostics.domain.DataMode;
import com.leecardo.paymentdiagnostics.domain.DiagnosisEvidence;
import com.leecardo.paymentdiagnostics.domain.DiagnosisRuleId;
import com.leecardo.paymentdiagnostics.domain.DiagnosisStage;
import com.leecardo.paymentdiagnostics.domain.MessageDelivery;
import com.leecardo.paymentdiagnostics.domain.MessageDeliveryStatus;
import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.OrderRole;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;
import com.leecardo.paymentdiagnostics.domain.OrderStatus;
import com.leecardo.paymentdiagnostics.domain.PaymentStatus;
import com.leecardo.paymentdiagnostics.domain.PaymentTransaction;
import com.leecardo.paymentdiagnostics.domain.TraceSummary;

import org.junit.jupiter.api.Test;

class DeterministicDiagnosisRulesTest {

    private static final OrderId ORDER_ID = new OrderId("order-123");
    private static final Instant ORDERED_AT = Instant.parse("2026-08-14T10:00:00Z");
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-14T10:30:00Z");
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-14T10:05:00Z");
    private static final Instant PROVIDER_COMPLETED_AT = Instant.parse("2026-08-14T10:10:00Z");
    private static final Instant CALLBACK_RECEIVED_AT = Instant.parse("2026-08-14T10:12:00Z");
    private static final Instant MESSAGE_CREATED_AT = Instant.parse("2026-08-14T10:13:00Z");
    private static final Instant SENT_AT = Instant.parse("2026-08-14T10:14:00Z");
    private static final Instant CONSUMED_AT = Instant.parse("2026-08-14T10:16:00Z");
    private static final Instant COMPENSATION_CREATED_AT = Instant.parse("2026-08-14T10:17:00Z");

    private static final DiagnosisPolicy POLICY = new DiagnosisPolicy(Duration.ofMinutes(10), Duration.ofMinutes(5));
    private static final DeterministicDiagnosisRules RULES = new DeterministicDiagnosisRules(POLICY);

    @Test
    void paymentNotStartedWhenOrderStillAwaitingPaymentAndNoPaymentExists() {
        assertDiagnosis(
                RULES.diagnose(facts(order(OrderStatus.PENDING_PAYMENT, ORDERED_AT.plusSeconds(30)), List.of(), List.of(), List.of(), trace())),
                DiagnosisRuleId.PAYMENT_NOT_STARTED,
                DiagnosisStage.PAYMENT_REQUESTED,
                evidence("order:order-123", "order", ORDERED_AT.plusSeconds(30)));
    }

    @Test
    void paymentProcessingTimeoutWhenProcessingAgeIsGreaterThanThreshold() {
        PaymentTransaction payment = payment("payment-timeout", PaymentStatus.PROCESSING, REQUESTED_AT, null, null, null, null);

        assertDiagnosis(
                RULES.diagnose(facts(List.of(payment), List.of(), List.of(), trace(), REQUESTED_AT.plus(POLICY.paymentProcessingTimeout()).plusSeconds(1))),
                DiagnosisRuleId.PAYMENT_PROCESSING_TIMEOUT,
                DiagnosisStage.PAYMENT_CONFIRMED,
                evidence("payment:payment-timeout", "payment", REQUESTED_AT));
    }

    @Test
    void paymentProcessingAtThresholdIsNotTimedOut() {
        PaymentTransaction payment = payment("payment-at-threshold", PaymentStatus.PROCESSING, REQUESTED_AT, null, null, null, null);

        assertEquals(
                DiagnosisRuleId.INSUFFICIENT_EVIDENCE,
                RULES.diagnose(facts(List.of(payment), List.of(), List.of(), trace(), REQUESTED_AT.plus(POLICY.paymentProcessingTimeout()))).ruleId());
    }

    @Test
    void providerSucceededCallbackMissingWhenProviderCompletedButCallbackAbsent() {
        PaymentTransaction payment = payment("payment-provider-success", PaymentStatus.PROVIDER_SUCCEEDED,
                REQUESTED_AT, PROVIDER_COMPLETED_AT, null, null, null);

        assertDiagnosis(
                RULES.diagnose(facts(List.of(payment), List.of(), List.of(), trace(), OBSERVED_AT)),
                DiagnosisRuleId.PROVIDER_SUCCEEDED_CALLBACK_MISSING,
                DiagnosisStage.PAYMENT_CALLBACK,
                evidence("payment:payment-provider-success", "payment", PROVIDER_COMPLETED_AT));
    }

    @Test
    void callbackSucceededOrderNotUpdatedWhenCallbackReceivedButOrderStillPending() {
        PaymentTransaction payment = callbackPayment("payment-callback");

        assertDiagnosis(
                RULES.diagnose(facts(order(OrderStatus.PENDING_PAYMENT, ORDERED_AT.plusSeconds(30)), List.of(payment), List.of(), List.of(), trace())),
                DiagnosisRuleId.CALLBACK_SUCCEEDED_ORDER_NOT_UPDATED,
                DiagnosisStage.ORDER_STATE_UPDATE,
                evidence("payment:payment-callback", "payment", CALLBACK_RECEIVED_AT));
    }

    @Test
    void paymentFailedWithProviderErrorUsesProviderErrorFacts() {
        PaymentTransaction payment = payment("payment-failed", PaymentStatus.FAILED,
                REQUESTED_AT, PROVIDER_COMPLETED_AT, null, "P001", "provider timeout");

        assertDiagnosis(
                RULES.diagnose(facts(List.of(payment), List.of(), List.of(), trace(), OBSERVED_AT)),
                DiagnosisRuleId.PAYMENT_FAILED_WITH_PROVIDER_ERROR,
                DiagnosisStage.PAYMENT_CONFIRMED,
                evidence("payment:payment-failed", "payment", PROVIDER_COMPLETED_AT));
    }

    @Test
    void messageNotSentOnlyWhenSuccessfulPaymentMeansDownstreamUpdateExpected() {
        PaymentTransaction payment = callbackPayment("payment-callback");

        assertDiagnosis(
                RULES.diagnose(facts(order(OrderStatus.PAID, CALLBACK_RECEIVED_AT), List.of(payment), List.of(), List.of(), trace())),
                DiagnosisRuleId.MESSAGE_NOT_SENT,
                DiagnosisStage.MESSAGE_DELIVERY,
                evidence("payment:payment-callback", "payment", CALLBACK_RECEIVED_AT));
    }

    @Test
    void messageNotSentDoesNotApplyWithoutSuccessfulPaymentFacts() {
        assertEquals(
                DiagnosisRuleId.INSUFFICIENT_EVIDENCE,
                RULES.diagnose(facts(order(OrderStatus.PAID, CALLBACK_RECEIVED_AT), List.of(), List.of(), List.of(), trace())).ruleId());
    }

    @Test
    void messageSendFailedUsesDeliveryError() {
        PaymentTransaction payment = callbackPayment("payment-callback");
        MessageDelivery delivery = message("message-send-failed", MessageDeliveryStatus.SEND_FAILED, null, null, "broker unavailable");

        assertDiagnosis(
                RULES.diagnose(facts(List.of(payment), List.of(delivery), List.of(), trace(), OBSERVED_AT)),
                DiagnosisRuleId.MESSAGE_SEND_FAILED,
                DiagnosisStage.MESSAGE_DELIVERY,
                evidence("message:message-send-failed", "message", MESSAGE_CREATED_AT));
    }

    @Test
    void explicitMessageSendFailedDoesNotRequireDownstreamUpdateTrigger() {
        MessageDelivery delivery = message("message-send-failed", MessageDeliveryStatus.SEND_FAILED, null, null, "broker unavailable");

        assertDiagnosis(
                RULES.diagnose(facts(order(OrderStatus.CANCELLED, CALLBACK_RECEIVED_AT), List.of(), List.of(delivery), List.of(), trace())),
                DiagnosisRuleId.MESSAGE_SEND_FAILED,
                DiagnosisStage.MESSAGE_DELIVERY,
                evidence("message:message-send-failed", "message", MESSAGE_CREATED_AT));
    }

    @Test
    void messageNotConsumedWhenSentAgeIsGreaterThanThreshold() {
        PaymentTransaction payment = callbackPayment("payment-callback");
        MessageDelivery delivery = message("message-sent", MessageDeliveryStatus.SENT, SENT_AT, null, null);

        assertDiagnosis(
                RULES.diagnose(facts(List.of(payment), List.of(delivery), List.of(), trace(), SENT_AT.plus(POLICY.messageConsumptionTimeout()).plusSeconds(1))),
                DiagnosisRuleId.MESSAGE_NOT_CONSUMED,
                DiagnosisStage.MESSAGE_DELIVERY,
                evidence("message:message-sent", "message", SENT_AT));
    }

    @Test
    void explicitMessageNotConsumedDoesNotRequireDownstreamUpdateTrigger() {
        MessageDelivery delivery = message("message-sent", MessageDeliveryStatus.SENT, SENT_AT, null, null);

        assertDiagnosis(
                RULES.diagnose(facts(order(OrderStatus.CANCELLED, CALLBACK_RECEIVED_AT), List.of(), List.of(delivery), List.of(), trace(), SENT_AT.plus(POLICY.messageConsumptionTimeout()).plusSeconds(1))),
                DiagnosisRuleId.MESSAGE_NOT_CONSUMED,
                DiagnosisStage.MESSAGE_DELIVERY,
                evidence("message:message-sent", "message", SENT_AT));
    }

    @Test
    void messageSentAtThresholdIsNotNotConsumed() {
        PaymentTransaction payment = callbackPayment("payment-callback");
        MessageDelivery delivery = message("message-at-threshold", MessageDeliveryStatus.SENT, SENT_AT, null, null);

        assertEquals(
                DiagnosisRuleId.INSUFFICIENT_EVIDENCE,
                RULES.diagnose(facts(List.of(payment), List.of(delivery), List.of(), trace(), SENT_AT.plus(POLICY.messageConsumptionTimeout()))).ruleId());
    }

    @Test
    void messageConsumeFailedUsesConsumerError() {
        PaymentTransaction payment = callbackPayment("payment-callback");
        MessageDelivery delivery = message("message-consume-failed", MessageDeliveryStatus.CONSUME_FAILED, SENT_AT, null, "consumer timeout");
        CompensationTask compensation = compensation("compensation-succeeded", CompensationStatus.SUCCEEDED, 1, 3, COMPENSATION_CREATED_AT, null);

        assertDiagnosis(
                RULES.diagnose(facts(List.of(payment), List.of(delivery), List.of(compensation), trace(), OBSERVED_AT)),
                DiagnosisRuleId.MESSAGE_CONSUME_FAILED,
                DiagnosisStage.MESSAGE_DELIVERY,
                evidence("message:message-consume-failed", "message", SENT_AT));
    }

    @Test
    void explicitMessageConsumeFailedDoesNotRequireDownstreamUpdateTrigger() {
        MessageDelivery delivery = message("message-consume-failed", MessageDeliveryStatus.CONSUME_FAILED, SENT_AT, null, "consumer timeout");

        assertDiagnosis(
                RULES.diagnose(facts(order(OrderStatus.CANCELLED, CALLBACK_RECEIVED_AT), List.of(), List.of(delivery), List.of(), trace())),
                DiagnosisRuleId.MESSAGE_CONSUME_FAILED,
                DiagnosisStage.MESSAGE_DELIVERY,
                evidence("message:message-consume-failed", "message", SENT_AT));
    }

    @Test
    void compensationNotCreatedWhenCancelledOrderContradictsSuccessfulPaidCallback() {
        PaymentTransaction payment = callbackPayment("payment-callback");

        assertDiagnosis(
                RULES.diagnose(facts(order(OrderStatus.CANCELLED, CALLBACK_RECEIVED_AT), List.of(payment), List.of(), List.of(), trace())),
                DiagnosisRuleId.COMPENSATION_NOT_CREATED,
                DiagnosisStage.COMPENSATION,
                evidence("order:order-123", "order", CALLBACK_RECEIVED_AT),
                evidence("payment:payment-callback", "payment", CALLBACK_RECEIVED_AT));
    }

    @Test
    void cancellationAloneDoesNotTriggerCompensation() {
        assertEquals(
                DiagnosisRuleId.INSUFFICIENT_EVIDENCE,
                RULES.diagnose(facts(order(OrderStatus.CANCELLED, CALLBACK_RECEIVED_AT), List.of(), List.of(), List.of(), trace())).ruleId());
    }

    @Test
    void successfulPaidCallbackAloneDoesNotTriggerCompensation() {
        PaymentTransaction payment = callbackPayment("payment-callback");
        MessageDelivery delivery = message("message-consumed", MessageDeliveryStatus.CONSUMED, SENT_AT, CONSUMED_AT, null);

        assertEquals(
                DiagnosisRuleId.NO_KNOWN_EXCEPTION,
                RULES.diagnose(facts(order(OrderStatus.PAID, CALLBACK_RECEIVED_AT), List.of(payment), List.of(delivery), List.of(), trace())).ruleId());
    }

    @Test
    void compensationFailedUsesRetryableTaskError() {
        PaymentTransaction payment = callbackPayment("payment-callback");
        CompensationTask compensation = compensation("compensation-failed", CompensationStatus.FAILED, 1, 3, OBSERVED_AT.minusSeconds(60), "gateway timeout");

        assertDiagnosis(
                RULES.diagnose(facts(order(OrderStatus.CANCELLED, CALLBACK_RECEIVED_AT), List.of(payment), List.of(), List.of(compensation), trace())),
                DiagnosisRuleId.COMPENSATION_FAILED,
                DiagnosisStage.COMPENSATION,
                evidence("compensation:compensation-failed", "compensation", OBSERVED_AT.minusSeconds(60)));
    }

    @Test
    void explicitCompensationFailureDoesNotRequireCompensationTrigger() {
        CompensationTask compensation = compensation("compensation-failed", CompensationStatus.FAILED, 1, 3, OBSERVED_AT.minusSeconds(60), "gateway timeout");

        assertDiagnosis(
                RULES.diagnose(facts(order(OrderStatus.PAID, CALLBACK_RECEIVED_AT), List.of(), List.of(), List.of(compensation), trace())),
                DiagnosisRuleId.COMPENSATION_FAILED,
                DiagnosisStage.COMPENSATION,
                evidence("compensation:compensation-failed", "compensation", OBSERVED_AT.minusSeconds(60)));
    }

    @Test
    void compensationRetriesExhaustedPrecedesRetryableCompensationFailure() {
        PaymentTransaction payment = callbackPayment("payment-callback");
        CompensationTask failed = compensation("compensation-failed", CompensationStatus.FAILED, 1, 3, OBSERVED_AT.minusSeconds(120), "gateway timeout");
        CompensationTask exhausted = compensation("compensation-exhausted", CompensationStatus.RETRIES_EXHAUSTED, 3, 3, OBSERVED_AT.minusSeconds(60), "gateway timeout");

        assertDiagnosis(
                RULES.diagnose(facts(order(OrderStatus.CANCELLED, CALLBACK_RECEIVED_AT), List.of(payment), List.of(), List.of(failed, exhausted), trace())),
                DiagnosisRuleId.COMPENSATION_RETRIES_EXHAUSTED,
                DiagnosisStage.COMPENSATION,
                evidence("compensation:compensation-exhausted", "compensation", OBSERVED_AT.minusSeconds(60)));
    }

    @Test
    void explicitCompensationRetriesExhaustedDoesNotRequireCompensationTrigger() {
        CompensationTask compensation = compensation("compensation-exhausted", CompensationStatus.RETRIES_EXHAUSTED, 3, 3, OBSERVED_AT.minusSeconds(60), "gateway timeout");

        assertDiagnosis(
                RULES.diagnose(facts(order(OrderStatus.PAID, CALLBACK_RECEIVED_AT), List.of(), List.of(), List.of(compensation), trace())),
                DiagnosisRuleId.COMPENSATION_RETRIES_EXHAUSTED,
                DiagnosisStage.COMPENSATION,
                evidence("compensation:compensation-exhausted", "compensation", OBSERVED_AT.minusSeconds(60)));
    }

    @Test
    void traceMissingOnlyWhenNoStrongerRuleApplies() {
        PaymentTransaction payment = callbackPayment("payment-callback");
        MessageDelivery delivery = message("message-consumed", MessageDeliveryStatus.CONSUMED, SENT_AT, CONSUMED_AT, null);
        CompensationTask compensation = compensation("compensation-succeeded", CompensationStatus.SUCCEEDED, 1, 3, COMPENSATION_CREATED_AT, null);

        assertDiagnosis(
                RULES.diagnose(facts(List.of(payment), List.of(delivery), List.of(compensation), Optional.empty(), OBSERVED_AT)),
                DiagnosisRuleId.TRACE_MISSING,
                DiagnosisStage.TRACE_CORRELATION,
                evidence("order:order-123", "trace", OBSERVED_AT));
    }

    @Test
    void strongerMessageRulePrecedesTraceMissing() {
        PaymentTransaction payment = callbackPayment("payment-callback");
        MessageDelivery delivery = message("message-send-failed", MessageDeliveryStatus.SEND_FAILED, null, null, "broker unavailable");

        assertEquals(
                DiagnosisRuleId.MESSAGE_SEND_FAILED,
                RULES.diagnose(facts(List.of(payment), List.of(delivery), List.of(), Optional.empty(), OBSERVED_AT)).ruleId());
    }

    @Test
    void insufficientEvidenceWhenNoApprovedRuleCanUseKnownFacts() {
        assertDiagnosis(
                RULES.diagnose(facts(order(OrderStatus.OUTBOUND, OBSERVED_AT.minusSeconds(60)), List.of(), List.of(), List.of(), trace())),
                DiagnosisRuleId.INSUFFICIENT_EVIDENCE,
                DiagnosisStage.INSUFFICIENT_EVIDENCE);
    }

    @Test
    void noKnownExceptionWhenPaymentMessageCompensationAndTraceAreComplete() {
        PaymentTransaction payment = callbackPayment("payment-callback");
        MessageDelivery delivery = message("message-consumed", MessageDeliveryStatus.CONSUMED, SENT_AT, CONSUMED_AT, null);
        CompensationTask compensation = compensation("compensation-succeeded", CompensationStatus.SUCCEEDED, 1, 3, COMPENSATION_CREATED_AT, null);
        TraceSummary trace = trace("trace-complete", true);

        assertDiagnosis(
                RULES.diagnose(facts(List.of(payment), List.of(delivery), List.of(compensation), Optional.of(trace), OBSERVED_AT)),
                DiagnosisRuleId.NO_KNOWN_EXCEPTION,
                DiagnosisStage.COMPLETED,
                evidence("trace:trace-complete", "trace", OBSERVED_AT));
    }

    @Test
    void paymentFailurePrecedesApplicableMessageFailure() {
        PaymentTransaction callback = callbackPayment("payment-callback");
        PaymentTransaction failed = payment("payment-failed", PaymentStatus.FAILED,
                REQUESTED_AT, PROVIDER_COMPLETED_AT, null, "P001", "provider timeout");
        MessageDelivery delivery = message("message-send-failed", MessageDeliveryStatus.SEND_FAILED, null, null, "broker unavailable");

        assertEquals(
                DiagnosisRuleId.PAYMENT_FAILED_WITH_PROVIDER_ERROR,
                RULES.diagnose(facts(order(OrderStatus.PAID, CALLBACK_RECEIVED_AT), List.of(callback, failed), List.of(delivery), List.of(), trace())).ruleId());
    }

    @Test
    void paymentFailurePrecedesApplicableCompensationFailure() {
        PaymentTransaction callback = callbackPayment("payment-callback");
        PaymentTransaction failed = payment("payment-failed", PaymentStatus.FAILED,
                REQUESTED_AT, PROVIDER_COMPLETED_AT, null, "P001", "provider timeout");
        CompensationTask compensation = compensation("compensation-failed", CompensationStatus.FAILED, 1, 3, OBSERVED_AT.minusSeconds(60), "gateway timeout");

        assertEquals(
                DiagnosisRuleId.PAYMENT_FAILED_WITH_PROVIDER_ERROR,
                RULES.diagnose(facts(order(OrderStatus.CANCELLED, CALLBACK_RECEIVED_AT), List.of(callback, failed), List.of(), List.of(compensation), trace())).ruleId());
    }

    @Test
    void collectedFactsRequiresValuesCopiesListsAndNormalizesWarnings() {
        ArrayList<PaymentTransaction> payments = new ArrayList<>(List.of(callbackPayment("payment-callback")));
        ArrayList<String> warnings = new ArrayList<>(List.of(" delayed payment read "));

        CollectedFacts facts = new CollectedFacts(order(OrderStatus.PAID, CALLBACK_RECEIVED_AT), payments, List.of(), List.of(), trace(), OBSERVED_AT,
                DataMode.SIMULATION, warnings);
        payments.clear();
        warnings.set(0, "changed");

        assertEquals(1, facts.payments().size());
        assertEquals("delayed payment read", facts.warnings().get(0));
        assertNotSame(payments, facts.payments());
        assertThrows(UnsupportedOperationException.class, () -> facts.payments().add(callbackPayment("other")));
        assertThrows(NullPointerException.class, () -> new CollectedFacts(null, List.of(), List.of(), List.of(), trace(), OBSERVED_AT,
                DataMode.SIMULATION, List.of()));
        assertThrows(NullPointerException.class, () -> new CollectedFacts(order(OrderStatus.PAID, CALLBACK_RECEIVED_AT), null, List.of(), List.of(), trace(), OBSERVED_AT,
                DataMode.SIMULATION, List.of()));
        assertThrows(NullPointerException.class, () -> new CollectedFacts(order(OrderStatus.PAID, CALLBACK_RECEIVED_AT), List.of(), List.of(), List.of(), null, OBSERVED_AT,
                DataMode.SIMULATION, List.of()));
        assertThrows(NullPointerException.class, () -> new CollectedFacts(order(OrderStatus.PAID, CALLBACK_RECEIVED_AT), List.of(), List.of(), List.of(), trace(), null,
                DataMode.SIMULATION, List.of()));
        assertThrows(NullPointerException.class, () -> new CollectedFacts(order(OrderStatus.PAID, CALLBACK_RECEIVED_AT), List.of(), List.of(), List.of(), trace(), OBSERVED_AT,
                null, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CollectedFacts(order(OrderStatus.PAID, CALLBACK_RECEIVED_AT), List.of(), List.of(), List.of(), trace(), OBSERVED_AT,
                DataMode.SIMULATION, List.of(" ")));
    }

    @Test
    void diagnosisPolicyRequiresPositiveNonZeroDurations() {
        assertEquals(Duration.ofMinutes(1), new DiagnosisPolicy(Duration.ofMinutes(1), Duration.ofMinutes(2)).paymentProcessingTimeout());
        assertThrows(NullPointerException.class, () -> new DiagnosisPolicy(null, Duration.ofMinutes(1)));
        assertThrows(NullPointerException.class, () -> new DiagnosisPolicy(Duration.ofMinutes(1), null));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosisPolicy(Duration.ZERO, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosisPolicy(Duration.ofMinutes(-1), Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> new DiagnosisPolicy(Duration.ofMinutes(1), Duration.ZERO));
    }

    @Test
    void everyNonExemptRuleReferencesActualFacts() {
        List<DiagnosisRuleId> nonExemptRules = List.of(
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
                DiagnosisRuleId.TRACE_MISSING);

        Set<String> actualFactIds = Set.of(
                "order:order-123",
                "payment:payment-timeout",
                "payment:payment-provider-success",
                "payment:payment-callback",
                "payment:payment-failed",
                "message:message-send-failed",
                "message:message-sent",
                "message:message-consume-failed",
                "compensation:compensation-failed",
                "compensation:compensation-exhausted",
                "trace:trace-complete");

        for (DiagnosisRuleId ruleId : nonExemptRules) {
            assertTrue(sample(ruleId).evidence().stream().allMatch(evidence -> actualFactIds.contains(evidence.id())), ruleId.name());
        }
    }

    @Test
    void evidenceSummariesAreFactualObservations() {
        List<DiagnosisRuleId> nonExemptRules = List.of(
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
                DiagnosisRuleId.TRACE_MISSING);

        for (DiagnosisRuleId ruleId : nonExemptRules) {
            for (DiagnosisEvidence evidence : sample(ruleId).evidence()) {
                assertTrue(evidence.summary().contains("=") || evidence.summary().endsWith("false"), ruleId + " " + evidence.summary());
                assertTextAbsent(evidence.summary(), "requires");
                assertTextAbsent(evidence.summary(), "within the configured timeout");
                assertTextAbsent(evidence.summary(), "missing");
                assertTextAbsent(evidence.summary(), "no known exception");
                assertTextAbsent(evidence.summary(), "remains");
                assertTextAbsent(evidence.summary(), "should");
            }
        }
    }

    private static void assertDiagnosis(
            com.leecardo.paymentdiagnostics.domain.DiagnosisResult result,
            DiagnosisRuleId ruleId,
            DiagnosisStage stage,
            ExpectedEvidence... expectedEvidence) {
        assertEquals(ruleId, result.ruleId());
        assertEquals(stage, result.stage());
        assertEquals(ORDER_ID, result.orderId());
        assertEquals(DataMode.SIMULATION, result.dataMode());
        assertEquals(List.of("partial trace unavailable"), result.warnings());
        assertEquals(expectedEvidence.length, result.evidence().size());
        for (int index = 0; index < expectedEvidence.length; index++) {
            ExpectedEvidence expected = expectedEvidence[index];
            DiagnosisEvidence actual = result.evidence().get(index);
            assertEquals(expected.id(), actual.id());
            assertEquals(expected.source(), actual.source());
            assertEquals(expected.observedAt(), actual.observedAt());
        }
    }

    private static void assertTextAbsent(String text, String forbidden) {
        assertTrue(!text.toLowerCase().contains(forbidden), text);
    }

    private static void assertDiagnosis(
            com.leecardo.paymentdiagnostics.domain.DiagnosisResult result,
            DiagnosisRuleId ruleId,
            DiagnosisStage stage) {
        assertDiagnosis(result, ruleId, stage, new ExpectedEvidence[0]);
    }

    private static com.leecardo.paymentdiagnostics.domain.DiagnosisResult sample(DiagnosisRuleId ruleId) {
        return switch (ruleId) {
            case PAYMENT_NOT_STARTED -> RULES.diagnose(facts(order(OrderStatus.PENDING_PAYMENT, ORDERED_AT.plusSeconds(30)), List.of(), List.of(), List.of(), trace()));
            case PAYMENT_PROCESSING_TIMEOUT -> RULES.diagnose(facts(List.of(payment("payment-timeout", PaymentStatus.PROCESSING, REQUESTED_AT, null, null, null, null)), List.of(), List.of(), trace(), REQUESTED_AT.plus(POLICY.paymentProcessingTimeout()).plusSeconds(1)));
            case PROVIDER_SUCCEEDED_CALLBACK_MISSING -> RULES.diagnose(facts(List.of(payment("payment-provider-success", PaymentStatus.PROVIDER_SUCCEEDED, REQUESTED_AT, PROVIDER_COMPLETED_AT, null, null, null)), List.of(), List.of(), trace(), OBSERVED_AT));
            case CALLBACK_SUCCEEDED_ORDER_NOT_UPDATED -> RULES.diagnose(facts(order(OrderStatus.PENDING_PAYMENT, ORDERED_AT.plusSeconds(30)), List.of(callbackPayment("payment-callback")), List.of(), List.of(), trace()));
            case PAYMENT_FAILED_WITH_PROVIDER_ERROR -> RULES.diagnose(facts(List.of(payment("payment-failed", PaymentStatus.FAILED, REQUESTED_AT, PROVIDER_COMPLETED_AT, null, "P001", "provider timeout")), List.of(), List.of(), trace(), OBSERVED_AT));
            case MESSAGE_NOT_SENT -> RULES.diagnose(facts(order(OrderStatus.PAID, CALLBACK_RECEIVED_AT), List.of(callbackPayment("payment-callback")), List.of(), List.of(), trace()));
            case MESSAGE_SEND_FAILED -> RULES.diagnose(facts(List.of(callbackPayment("payment-callback")), List.of(message("message-send-failed", MessageDeliveryStatus.SEND_FAILED, null, null, "broker unavailable")), List.of(), trace(), OBSERVED_AT));
            case MESSAGE_NOT_CONSUMED -> RULES.diagnose(facts(List.of(callbackPayment("payment-callback")), List.of(message("message-sent", MessageDeliveryStatus.SENT, SENT_AT, null, null)), List.of(), trace(), SENT_AT.plus(POLICY.messageConsumptionTimeout()).plusSeconds(1)));
            case MESSAGE_CONSUME_FAILED -> RULES.diagnose(facts(List.of(callbackPayment("payment-callback")), List.of(message("message-consume-failed", MessageDeliveryStatus.CONSUME_FAILED, SENT_AT, null, "consumer timeout")), List.of(compensation("compensation-succeeded", CompensationStatus.SUCCEEDED, 1, 3, COMPENSATION_CREATED_AT, null)), trace(), OBSERVED_AT));
            case COMPENSATION_NOT_CREATED -> RULES.diagnose(facts(order(OrderStatus.CANCELLED, CALLBACK_RECEIVED_AT), List.of(callbackPayment("payment-callback")), List.of(), List.of(), trace()));
            case COMPENSATION_FAILED -> RULES.diagnose(facts(order(OrderStatus.CANCELLED, CALLBACK_RECEIVED_AT), List.of(callbackPayment("payment-callback")), List.of(), List.of(compensation("compensation-failed", CompensationStatus.FAILED, 1, 3, OBSERVED_AT.minusSeconds(60), "gateway timeout")), trace()));
            case COMPENSATION_RETRIES_EXHAUSTED -> RULES.diagnose(facts(order(OrderStatus.CANCELLED, CALLBACK_RECEIVED_AT), List.of(callbackPayment("payment-callback")), List.of(), List.of(compensation("compensation-exhausted", CompensationStatus.RETRIES_EXHAUSTED, 3, 3, OBSERVED_AT.minusSeconds(60), "gateway timeout")), trace()));
            case TRACE_MISSING -> RULES.diagnose(facts(List.of(callbackPayment("payment-callback")), List.of(message("message-consumed", MessageDeliveryStatus.CONSUMED, SENT_AT, CONSUMED_AT, null)), List.of(compensation("compensation-succeeded", CompensationStatus.SUCCEEDED, 1, 3, COMPENSATION_CREATED_AT, null)), Optional.empty(), OBSERVED_AT));
            default -> throw new IllegalArgumentException("No non-exempt sample for " + ruleId);
        };
    }

    private static CollectedFacts facts(
            List<PaymentTransaction> payments,
            List<MessageDelivery> messages,
            List<CompensationTask> compensations,
            Optional<TraceSummary> trace,
            Instant observedAt) {
        return facts(order(OrderStatus.PAID, CALLBACK_RECEIVED_AT), payments, messages, compensations, trace, observedAt);
    }

    private static CollectedFacts facts(
            OrderSnapshot order,
            List<PaymentTransaction> payments,
            List<MessageDelivery> messages,
            List<CompensationTask> compensations,
            Optional<TraceSummary> trace) {
        return facts(order, payments, messages, compensations, trace, OBSERVED_AT);
    }

    private static CollectedFacts facts(
            OrderSnapshot order,
            List<PaymentTransaction> payments,
            List<MessageDelivery> messages,
            List<CompensationTask> compensations,
            Optional<TraceSummary> trace,
            Instant observedAt) {
        return new CollectedFacts(order, payments, messages, compensations, trace, observedAt, DataMode.SIMULATION,
                List.of(" partial trace unavailable "));
    }

    private static Optional<TraceSummary> trace() {
        return Optional.of(trace("trace-complete", true));
    }

    private static TraceSummary trace(String traceId, boolean complete) {
        return new TraceSummary(traceId, ORDER_ID, "corr-123", REQUESTED_AT, OBSERVED_AT, complete, "complete payment trace");
    }

    private static PaymentTransaction callbackPayment(String transactionId) {
        return payment(transactionId, PaymentStatus.CALLBACK_RECEIVED, REQUESTED_AT, PROVIDER_COMPLETED_AT, CALLBACK_RECEIVED_AT, null, null);
    }

    private static PaymentTransaction payment(
            String transactionId,
            PaymentStatus status,
            Instant requestedAt,
            Instant providerCompletedAt,
            Instant callbackReceivedAt,
            String providerErrorCode,
            String providerErrorSummary) {
        return new PaymentTransaction(
                transactionId,
                ORDER_ID,
                "stripe",
                new BigDecimal("39.80"),
                status,
                requestedAt,
                providerCompletedAt,
                callbackReceivedAt,
                providerErrorCode,
                providerErrorSummary);
    }

    private static MessageDelivery message(
            String deliveryId,
            MessageDeliveryStatus status,
            Instant sentAt,
            Instant consumedAt,
            String lastError) {
        return new MessageDelivery(
                deliveryId,
                ORDER_ID,
                "PaymentCompleted",
                "corr-123",
                status,
                MESSAGE_CREATED_AT,
                sentAt,
                consumedAt,
                lastError);
    }

    private static CompensationTask compensation(
            String taskId,
            CompensationStatus status,
            int retryCount,
            int maxRetries,
            Instant lastAttemptAt,
            String lastError) {
        return new CompensationTask(
                taskId,
                ORDER_ID,
                "reverse-payment",
                status,
                retryCount,
                maxRetries,
                COMPENSATION_CREATED_AT,
                lastAttemptAt,
                lastError);
    }

    private static OrderSnapshot order(OrderStatus status, Instant stateChangedAt) {
        return new OrderSnapshot(
                ORDER_ID,
                null,
                OrderRole.SINGLE,
                "product-001",
                "Diagnostic Product",
                "course",
                1,
                new BigDecimal("39.80"),
                new BigDecimal("39.80"),
                new BigDecimal("39.80"),
                "stripe",
                "provider-order-123",
                "web",
                status,
                ORDERED_AT,
                stateChangedAt,
                ORDERED_AT,
                stateChangedAt == null ? ORDERED_AT : stateChangedAt);
    }

    private record ExpectedEvidence(String id, String source, Instant observedAt) {
    }

    private static ExpectedEvidence evidence(String id, String source, Instant observedAt) {
        return new ExpectedEvidence(id, source, observedAt);
    }
}
