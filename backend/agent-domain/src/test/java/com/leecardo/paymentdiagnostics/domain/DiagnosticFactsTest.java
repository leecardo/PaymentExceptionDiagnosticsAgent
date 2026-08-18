package com.leecardo.paymentdiagnostics.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class DiagnosticFactsTest {

    private static final OrderId ORDER_ID = new OrderId(" order-123 ");
    private static final Instant REQUESTED_AT = Instant.parse("2026-08-14T10:00:00Z");
    private static final Instant PROVIDER_COMPLETED_AT = Instant.parse("2026-08-14T10:03:00Z");
    private static final Instant CALLBACK_RECEIVED_AT = Instant.parse("2026-08-14T10:05:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-08-14T10:01:00Z");
    private static final Instant SENT_AT = Instant.parse("2026-08-14T10:02:00Z");
    private static final Instant CONSUMED_AT = Instant.parse("2026-08-14T10:04:00Z");

    @Test
    void paymentTransactionStripsTextAndNormalizesOptionalErrorText() {
        PaymentTransaction transaction = payment(
                " tx-123 ",
                " stripe ",
                new BigDecimal("19.90"),
                PaymentStatus.REQUESTED,
                null,
                null,
                " ",
                "\t");

        assertEquals("tx-123", transaction.transactionId());
        assertEquals("stripe", transaction.provider());
        assertNull(transaction.providerErrorCode());
        assertNull(transaction.providerErrorSummary());
    }

    @Test
    void paymentTransactionRejectsMissingRequiredValuesAndBlankRequiredText() {
        assertThrows(NullPointerException.class, () -> payment(null, "stripe", BigDecimal.ZERO, PaymentStatus.REQUESTED,
                null, null, null, null));
        assertThrows(NullPointerException.class, () -> new PaymentTransaction("tx-123", null, "stripe", BigDecimal.ZERO,
                PaymentStatus.REQUESTED, REQUESTED_AT, null, null, null, null));
        assertThrows(NullPointerException.class, () -> payment("tx-123", "stripe", null, PaymentStatus.REQUESTED,
                null, null, null, null));
        assertThrows(NullPointerException.class, () -> payment("tx-123", "stripe", BigDecimal.ZERO, null,
                null, null, null, null));
        assertThrows(NullPointerException.class, () -> new PaymentTransaction("tx-123", ORDER_ID, "stripe", BigDecimal.ZERO,
                PaymentStatus.REQUESTED, null, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> payment(" ", "stripe", BigDecimal.ZERO, PaymentStatus.REQUESTED,
                null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> payment("tx-123", "\t", BigDecimal.ZERO, PaymentStatus.REQUESTED,
                null, null, null, null));
    }

    @Test
    void paymentTransactionRejectsNegativeAmountAndTemporalRegression() {
        assertThrows(IllegalArgumentException.class, () -> payment("tx-123", "stripe", new BigDecimal("-0.01"),
                PaymentStatus.REQUESTED, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> payment("tx-123", "stripe", BigDecimal.ZERO,
                PaymentStatus.PROVIDER_SUCCEEDED, REQUESTED_AT.minusSeconds(1), null, null, null));
        assertThrows(IllegalArgumentException.class, () -> payment("tx-123", "stripe", BigDecimal.ZERO,
                PaymentStatus.CALLBACK_RECEIVED, PROVIDER_COMPLETED_AT, REQUESTED_AT.minusSeconds(1), null, null));
        assertThrows(IllegalArgumentException.class, () -> payment("tx-123", "stripe", BigDecimal.ZERO,
                PaymentStatus.FAILED, CALLBACK_RECEIVED_AT, PROVIDER_COMPLETED_AT, "P001", "provider timeout"));
    }

    @Test
    void paymentTransactionEnforcesStatusSpecificFacts() {
        assertThrows(IllegalArgumentException.class, () -> payment("tx-123", "stripe", BigDecimal.ZERO,
                PaymentStatus.REQUESTED, PROVIDER_COMPLETED_AT, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> payment("tx-123", "stripe", BigDecimal.ZERO,
                PaymentStatus.PROCESSING, null, CALLBACK_RECEIVED_AT, null, null));
        assertThrows(IllegalArgumentException.class, () -> payment("tx-123", "stripe", BigDecimal.ZERO,
                PaymentStatus.PROVIDER_SUCCEEDED, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> payment("tx-123", "stripe", BigDecimal.ZERO,
                PaymentStatus.PROVIDER_SUCCEEDED, PROVIDER_COMPLETED_AT, null, "P001", null));
        assertThrows(IllegalArgumentException.class, () -> payment("tx-123", "stripe", BigDecimal.ZERO,
                PaymentStatus.CALLBACK_RECEIVED, PROVIDER_COMPLETED_AT, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> payment("tx-123", "stripe", BigDecimal.ZERO,
                PaymentStatus.CALLBACK_RECEIVED, CALLBACK_RECEIVED_AT, PROVIDER_COMPLETED_AT, null, null));

        PaymentTransaction providerSucceeded = payment("tx-123", "stripe", BigDecimal.ZERO,
                PaymentStatus.PROVIDER_SUCCEEDED, PROVIDER_COMPLETED_AT, null, null, null);
        PaymentTransaction callbackReceived = payment("tx-456", "stripe", BigDecimal.ZERO,
                PaymentStatus.CALLBACK_RECEIVED, PROVIDER_COMPLETED_AT, CALLBACK_RECEIVED_AT, null, null);

        assertEquals(PROVIDER_COMPLETED_AT, providerSucceeded.providerCompletedAt());
        assertEquals(CALLBACK_RECEIVED_AT, callbackReceived.callbackReceivedAt());
    }

    @Test
    void failedPaymentRequiresProviderErrorCodeAndSummary() {
        assertThrows(IllegalArgumentException.class, () -> payment("tx-123", "stripe", BigDecimal.ZERO,
                PaymentStatus.FAILED, PROVIDER_COMPLETED_AT, null, null, "provider timeout"));
        assertThrows(IllegalArgumentException.class, () -> payment("tx-123", "stripe", BigDecimal.ZERO,
                PaymentStatus.FAILED, PROVIDER_COMPLETED_AT, null, "P001", " "));

        PaymentTransaction failed = payment("tx-123", "stripe", BigDecimal.ZERO,
                PaymentStatus.FAILED, PROVIDER_COMPLETED_AT, null, " P001 ", " provider timeout ");

        assertEquals("P001", failed.providerErrorCode());
        assertEquals("provider timeout", failed.providerErrorSummary());
    }

    @Test
    void messageDeliveryStripsRequiredTextAndNormalizesOptionalErrorText() {
        MessageDelivery delivery = message(
                " delivery-123 ",
                " PaymentRequested ",
                " corr-123 ",
                MessageDeliveryStatus.PENDING,
                null,
                null,
                " ");

        assertEquals("delivery-123", delivery.deliveryId());
        assertEquals("PaymentRequested", delivery.eventType());
        assertEquals("corr-123", delivery.correlationId());
        assertNull(delivery.lastError());
    }

    @Test
    void messageDeliveryAcceptsEveryNonPendingStatusShape() {
        MessageDelivery sent = message("delivery-sent", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.SENT, SENT_AT, null, null);
        MessageDelivery sendFailed = message("delivery-send-failed", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.SEND_FAILED, null, null, " broker unavailable ");
        MessageDelivery consumed = message("delivery-consumed", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.CONSUMED, SENT_AT, CONSUMED_AT, null);
        MessageDelivery consumeFailed = message("delivery-consume-failed", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.CONSUME_FAILED, SENT_AT, null, " consumer timeout ");

        assertEquals(SENT_AT, sent.sentAt());
        assertNull(sent.consumedAt());
        assertNull(sent.lastError());
        assertNull(sendFailed.sentAt());
        assertEquals("broker unavailable", sendFailed.lastError());
        assertEquals(SENT_AT, consumed.sentAt());
        assertEquals(CONSUMED_AT, consumed.consumedAt());
        assertNull(consumed.lastError());
        assertEquals(SENT_AT, consumeFailed.sentAt());
        assertNull(consumeFailed.consumedAt());
        assertEquals("consumer timeout", consumeFailed.lastError());
    }

    @Test
    void messageDeliveryRejectsMissingRequiredValuesAndBlankRequiredText() {
        assertThrows(NullPointerException.class, () -> message(null, "PaymentRequested", "corr-123",
                MessageDeliveryStatus.PENDING, null, null, null));
        assertThrows(NullPointerException.class, () -> new MessageDelivery("delivery-123", null, "PaymentRequested", "corr-123",
                MessageDeliveryStatus.PENDING, CREATED_AT, null, null, null));
        assertThrows(NullPointerException.class, () -> message("delivery-123", "PaymentRequested", "corr-123",
                null, null, null, null));
        assertThrows(NullPointerException.class, () -> new MessageDelivery("delivery-123", ORDER_ID, "PaymentRequested", "corr-123",
                MessageDeliveryStatus.PENDING, null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> message(" ", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.PENDING, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> message("delivery-123", "\t", "corr-123",
                MessageDeliveryStatus.PENDING, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> message("delivery-123", "PaymentRequested", " ",
                MessageDeliveryStatus.PENDING, null, null, null));
    }

    @Test
    void messageDeliveryEnforcesStatusSpecificTimestampAndErrorFacts() {
        assertThrows(IllegalArgumentException.class, () -> message("delivery-123", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.PENDING, SENT_AT, null, null));
        assertThrows(IllegalArgumentException.class, () -> message("delivery-123", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.SENT, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> message("delivery-123", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.SENT, SENT_AT, null, "broker unavailable"));
        assertThrows(IllegalArgumentException.class, () -> message("delivery-123", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.SEND_FAILED, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> message("delivery-123", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.SEND_FAILED, SENT_AT, null, "broker unavailable"));
        assertThrows(IllegalArgumentException.class, () -> message("delivery-123", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.CONSUMED, SENT_AT, null, null));
        assertThrows(IllegalArgumentException.class, () -> message("delivery-123", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.CONSUME_FAILED, null, null, "consumer timeout"));
        assertThrows(IllegalArgumentException.class, () -> message("delivery-123", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.CONSUME_FAILED, SENT_AT, CONSUMED_AT, "consumer timeout"));
    }

    @Test
    void messageDeliveryRejectsTemporalRegression() {
        assertThrows(IllegalArgumentException.class, () -> message("delivery-123", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.SENT, CREATED_AT.minusSeconds(1), null, null));
        assertThrows(IllegalArgumentException.class, () -> message("delivery-123", "PaymentRequested", "corr-123",
                MessageDeliveryStatus.CONSUMED, SENT_AT, SENT_AT.minusSeconds(1), null));
    }

    @Test
    void compensationTaskStripsTextAndNormalizesOptionalErrorText() {
        CompensationTask task = compensation(
                " task-123 ",
                " reverse-payment ",
                CompensationStatus.PENDING,
                0,
                3,
                null,
                " ");

        assertEquals("task-123", task.taskId());
        assertEquals("reverse-payment", task.action());
        assertNull(task.lastError());
    }

    @Test
    void compensationTaskAcceptsRunningSucceededAndFailedStatusShapes() {
        CompensationTask running = compensation("task-running", "reverse-payment", CompensationStatus.RUNNING,
                1, 3, SENT_AT, null);
        CompensationTask succeeded = compensation("task-succeeded", "reverse-payment", CompensationStatus.SUCCEEDED,
                2, 3, SENT_AT, null);
        CompensationTask failed = compensation("task-failed", "reverse-payment", CompensationStatus.FAILED,
                1, 3, SENT_AT, " gateway timeout ");

        assertEquals(CompensationStatus.RUNNING, running.status());
        assertEquals(SENT_AT, running.lastAttemptAt());
        assertNull(running.lastError());
        assertEquals(CompensationStatus.SUCCEEDED, succeeded.status());
        assertEquals(SENT_AT, succeeded.lastAttemptAt());
        assertNull(succeeded.lastError());
        assertEquals(CompensationStatus.FAILED, failed.status());
        assertEquals("gateway timeout", failed.lastError());
    }

    @Test
    void compensationTaskRejectsMissingRequiredValuesAndBlankRequiredText() {
        assertThrows(NullPointerException.class, () -> compensation(null, "reverse-payment", CompensationStatus.PENDING,
                0, 3, null, null));
        assertThrows(NullPointerException.class, () -> new CompensationTask("task-123", null, "reverse-payment", CompensationStatus.PENDING,
                0, 3, CREATED_AT, null, null));
        assertThrows(NullPointerException.class, () -> compensation("task-123", "reverse-payment", null,
                0, 3, null, null));
        assertThrows(NullPointerException.class, () -> new CompensationTask("task-123", ORDER_ID, "reverse-payment", CompensationStatus.PENDING,
                0, 3, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> compensation(" ", "reverse-payment", CompensationStatus.PENDING,
                0, 3, null, null));
        assertThrows(IllegalArgumentException.class, () -> compensation("task-123", "\n", CompensationStatus.PENDING,
                0, 3, null, null));
    }

    @Test
    void compensationTaskRejectsInvalidRetryCountsAndTemporalRegression() {
        assertThrows(IllegalArgumentException.class, () -> compensation("task-123", "reverse-payment", CompensationStatus.PENDING,
                -1, 3, null, null));
        assertThrows(IllegalArgumentException.class, () -> compensation("task-123", "reverse-payment", CompensationStatus.PENDING,
                0, -1, null, null));
        assertThrows(IllegalArgumentException.class, () -> compensation("task-123", "reverse-payment", CompensationStatus.PENDING,
                4, 3, null, null));
        assertThrows(IllegalArgumentException.class, () -> compensation("task-123", "reverse-payment", CompensationStatus.RUNNING,
                1, 3, CREATED_AT.minusSeconds(1), null));
    }

    @Test
    void failedCompensationRequiresErrorAndRetriesExhaustedRequiresMaxRetriesAndError() {
        assertThrows(IllegalArgumentException.class, () -> compensation("task-123", "reverse-payment", CompensationStatus.FAILED,
                1, 3, SENT_AT, null));
        assertThrows(IllegalArgumentException.class, () -> compensation("task-123", "reverse-payment", CompensationStatus.RETRIES_EXHAUSTED,
                2, 3, SENT_AT, "gateway timeout"));
        assertThrows(IllegalArgumentException.class, () -> compensation("task-123", "reverse-payment", CompensationStatus.RETRIES_EXHAUSTED,
                3, 3, SENT_AT, " "));

        CompensationTask exhausted = compensation("task-123", "reverse-payment", CompensationStatus.RETRIES_EXHAUSTED,
                3, 3, SENT_AT, " gateway timeout ");

        assertEquals("gateway timeout", exhausted.lastError());
    }

    @Test
    void traceSummaryStripsRequiredText() {
        TraceSummary trace = trace(
                " trace-123 ",
                " corr-123 ",
                SENT_AT,
                " payment callback observed ");

        assertEquals("trace-123", trace.traceId());
        assertEquals("corr-123", trace.correlationId());
        assertEquals("payment callback observed", trace.summary());
    }

    @Test
    void traceSummaryRejectsMissingRequiredValuesAndBlankRequiredText() {
        assertThrows(NullPointerException.class, () -> trace(null, "corr-123", SENT_AT, "summary"));
        assertThrows(NullPointerException.class, () -> new TraceSummary("trace-123", null, "corr-123", CREATED_AT,
                SENT_AT, true, "summary"));
        assertThrows(NullPointerException.class, () -> trace("trace-123", null, SENT_AT, "summary"));
        assertThrows(NullPointerException.class, () -> new TraceSummary("trace-123", ORDER_ID, "corr-123", null,
                SENT_AT, true, "summary"));
        assertThrows(NullPointerException.class, () -> trace("trace-123", "corr-123", SENT_AT, null));
        assertThrows(IllegalArgumentException.class, () -> trace(" ", "corr-123", SENT_AT, "summary"));
        assertThrows(IllegalArgumentException.class, () -> trace("trace-123", " ", SENT_AT, "summary"));
        assertThrows(IllegalArgumentException.class, () -> trace("trace-123", "corr-123", SENT_AT, " "));
    }

    @Test
    void traceSummaryRejectsEndBeforeStart() {
        assertThrows(IllegalArgumentException.class, () -> trace("trace-123", "corr-123", CREATED_AT.minusSeconds(1),
                "summary"));
    }

    private static PaymentTransaction payment(
            String transactionId,
            String provider,
            BigDecimal amount,
            PaymentStatus status,
            Instant providerCompletedAt,
            Instant callbackReceivedAt,
            String providerErrorCode,
            String providerErrorSummary) {
        return new PaymentTransaction(
                transactionId,
                ORDER_ID,
                provider,
                amount,
                status,
                REQUESTED_AT,
                providerCompletedAt,
                callbackReceivedAt,
                providerErrorCode,
                providerErrorSummary);
    }

    private static MessageDelivery message(
            String deliveryId,
            String eventType,
            String correlationId,
            MessageDeliveryStatus status,
            Instant sentAt,
            Instant consumedAt,
            String lastError) {
        return new MessageDelivery(
                deliveryId,
                ORDER_ID,
                eventType,
                correlationId,
                status,
                CREATED_AT,
                sentAt,
                consumedAt,
                lastError);
    }

    private static CompensationTask compensation(
            String taskId,
            String action,
            CompensationStatus status,
            int retryCount,
            int maxRetries,
            Instant lastAttemptAt,
            String lastError) {
        return new CompensationTask(
                taskId,
                ORDER_ID,
                action,
                status,
                retryCount,
                maxRetries,
                CREATED_AT,
                lastAttemptAt,
                lastError);
    }

    private static TraceSummary trace(String traceId, String correlationId, Instant endedAt, String summary) {
        return new TraceSummary(traceId, ORDER_ID, correlationId, CREATED_AT, endedAt, true, summary);
    }
}
