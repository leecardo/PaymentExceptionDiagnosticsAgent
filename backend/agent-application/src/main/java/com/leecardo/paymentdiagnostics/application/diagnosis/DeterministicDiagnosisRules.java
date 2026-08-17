package com.leecardo.paymentdiagnostics.application.diagnosis;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.leecardo.paymentdiagnostics.domain.CompensationStatus;
import com.leecardo.paymentdiagnostics.domain.CompensationTask;
import com.leecardo.paymentdiagnostics.domain.DiagnosisEvidence;
import com.leecardo.paymentdiagnostics.domain.DiagnosisResult;
import com.leecardo.paymentdiagnostics.domain.DiagnosisRuleId;
import com.leecardo.paymentdiagnostics.domain.DiagnosisStage;
import com.leecardo.paymentdiagnostics.domain.MessageDelivery;
import com.leecardo.paymentdiagnostics.domain.MessageDeliveryStatus;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;
import com.leecardo.paymentdiagnostics.domain.OrderStatus;
import com.leecardo.paymentdiagnostics.domain.PaymentStatus;
import com.leecardo.paymentdiagnostics.domain.PaymentTransaction;
import com.leecardo.paymentdiagnostics.domain.TraceSummary;

public final class DeterministicDiagnosisRules {

    private final DiagnosisPolicy policy;

    public DeterministicDiagnosisRules(DiagnosisPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    public DiagnosisResult diagnose(CollectedFacts facts) {
        Objects.requireNonNull(facts, "facts must not be null");

        Optional<DiagnosisResult> paymentResult = diagnosePayment(facts);
        if (paymentResult.isPresent()) {
            return paymentResult.get();
        }

        Optional<DiagnosisResult> messageResult = diagnoseMessage(facts);
        if (messageResult.isPresent()) {
            return messageResult.get();
        }

        Optional<DiagnosisResult> compensationResult = diagnoseCompensation(facts);
        if (compensationResult.isPresent()) {
            return compensationResult.get();
        }

        if (facts.trace().isEmpty()) {
            return result(
                    facts,
                    DiagnosisStage.TRACE_CORRELATION,
                    DiagnosisRuleId.TRACE_MISSING,
                    "Trace summary missing for otherwise complete diagnostic facts",
                    List.of(new DiagnosisEvidence(
                            "order:" + facts.order().orderId().value(),
                            "trace",
                            "trace present=false",
                            facts.observedAt())));
        }

        if (completeChain(facts)) {
            TraceSummary trace = facts.trace().orElseThrow();
            return result(
                    facts,
                    DiagnosisStage.COMPLETED,
                    DiagnosisRuleId.NO_KNOWN_EXCEPTION,
                    "No known exception detected in completed payment flow",
                    List.of(traceEvidence(trace, traceStatusSummary(trace), traceObservedAt(trace))));
        }

        return result(
                facts,
                DiagnosisStage.INSUFFICIENT_EVIDENCE,
                DiagnosisRuleId.INSUFFICIENT_EVIDENCE,
                "Insufficient evidence to apply deterministic diagnosis rules",
                List.of());
    }

    private Optional<DiagnosisResult> diagnosePayment(CollectedFacts facts) {
        if (facts.payments().isEmpty() && facts.order().status() == OrderStatus.PENDING_PAYMENT) {
            return Optional.of(result(
                    facts,
                    DiagnosisStage.PAYMENT_REQUESTED,
                    DiagnosisRuleId.PAYMENT_NOT_STARTED,
                    "Order is pending payment and no payment transaction was found",
                    List.of(orderEvidence(facts.order(), orderStatusSummary(facts.order())))));
        }

        Optional<PaymentTransaction> timedOut = facts.payments().stream()
                .filter(payment -> payment.status() == PaymentStatus.PROCESSING || payment.status() == PaymentStatus.REQUESTED)
                .filter(payment -> olderThan(payment.requestedAt(), facts.observedAt(), policy.paymentProcessingTimeout()))
                .min(Comparator.comparing(PaymentTransaction::requestedAt));
        if (timedOut.isPresent()) {
            PaymentTransaction payment = timedOut.get();
            return Optional.of(result(
                    facts,
                    DiagnosisStage.PAYMENT_CONFIRMED,
                    DiagnosisRuleId.PAYMENT_PROCESSING_TIMEOUT,
                    "Payment transaction exceeded processing timeout",
                    List.of(paymentEvidence(payment, paymentStatusSummary(payment), payment.requestedAt()))));
        }

        Optional<PaymentTransaction> providerSucceededCallbackMissing = facts.payments().stream()
                .filter(payment -> payment.status() == PaymentStatus.PROVIDER_SUCCEEDED)
                .min(Comparator.comparing(PaymentTransaction::providerCompletedAt));
        if (providerSucceededCallbackMissing.isPresent()) {
            PaymentTransaction payment = providerSucceededCallbackMissing.get();
            return Optional.of(result(
                    facts,
                    DiagnosisStage.PAYMENT_CALLBACK,
                    DiagnosisRuleId.PROVIDER_SUCCEEDED_CALLBACK_MISSING,
                    "Provider completed payment but callback was not received",
                    List.of(paymentEvidence(payment, paymentStatusSummary(payment), payment.providerCompletedAt()))));
        }

        Optional<PaymentTransaction> callbackOrderNotUpdated = successfulPayments(facts).stream()
                .filter(payment -> facts.order().status() == OrderStatus.PENDING_PAYMENT)
                .min(Comparator.comparing(DeterministicDiagnosisRules::paymentSuccessAt));
        if (callbackOrderNotUpdated.isPresent()) {
            PaymentTransaction payment = callbackOrderNotUpdated.get();
            return Optional.of(result(
                    facts,
                    DiagnosisStage.ORDER_STATE_UPDATE,
                    DiagnosisRuleId.CALLBACK_SUCCEEDED_ORDER_NOT_UPDATED,
                    "Payment callback succeeded but order state was not updated",
                    List.of(paymentEvidence(payment, paymentStatusSummary(payment), paymentSuccessAt(payment)))));
        }

        Optional<PaymentTransaction> failed = facts.payments().stream()
                .filter(payment -> payment.status() == PaymentStatus.FAILED)
                .min(Comparator.comparing(DeterministicDiagnosisRules::paymentFailureAt));
        if (failed.isPresent()) {
            PaymentTransaction payment = failed.get();
            return Optional.of(result(
                    facts,
                    DiagnosisStage.PAYMENT_CONFIRMED,
                    DiagnosisRuleId.PAYMENT_FAILED_WITH_PROVIDER_ERROR,
                    "Payment provider reported a failure: " + payment.providerErrorCode(),
                    List.of(paymentEvidence(payment, paymentStatusSummary(payment), paymentFailureAt(payment)))));
        }

        return Optional.empty();
    }

    private Optional<DiagnosisResult> diagnoseMessage(CollectedFacts facts) {
        if (facts.messages().isEmpty()) {
            if (!downstreamUpdateExpected(facts)) {
                return Optional.empty();
            }
            PaymentTransaction payment = successfulPayments(facts).stream()
                    .min(Comparator.comparing(DeterministicDiagnosisRules::paymentSuccessAt))
                    .orElseThrow();
            return Optional.of(result(
                    facts,
                    DiagnosisStage.MESSAGE_DELIVERY,
                    DiagnosisRuleId.MESSAGE_NOT_SENT,
                    "Payment succeeded and order update expects downstream message, but no message delivery was found",
                    List.of(paymentEvidence(payment, paymentStatusSummary(payment), paymentSuccessAt(payment)))));
        }

        Optional<MessageDelivery> sendFailed = facts.messages().stream()
                .filter(message -> message.status() == MessageDeliveryStatus.SEND_FAILED)
                .min(Comparator.comparing(MessageDelivery::createdAt));
        if (sendFailed.isPresent()) {
            MessageDelivery message = sendFailed.get();
            return Optional.of(result(
                    facts,
                    DiagnosisStage.MESSAGE_DELIVERY,
                    DiagnosisRuleId.MESSAGE_SEND_FAILED,
                    "Message send failed: " + message.lastError(),
                    List.of(messageEvidence(message, messageStatusSummary(message), message.createdAt()))));
        }

        Optional<MessageDelivery> notConsumed = facts.messages().stream()
                .filter(message -> message.status() == MessageDeliveryStatus.SENT)
                .filter(message -> olderThan(message.sentAt(), facts.observedAt(), policy.messageConsumptionTimeout()))
                .min(Comparator.comparing(MessageDelivery::sentAt));
        if (notConsumed.isPresent()) {
            MessageDelivery message = notConsumed.get();
            return Optional.of(result(
                    facts,
                    DiagnosisStage.MESSAGE_DELIVERY,
                    DiagnosisRuleId.MESSAGE_NOT_CONSUMED,
                    "Message was sent but not consumed within the configured timeout",
                    List.of(messageEvidence(message, messageStatusSummary(message), message.sentAt()))));
        }

        Optional<MessageDelivery> consumeFailed = facts.messages().stream()
                .filter(message -> message.status() == MessageDeliveryStatus.CONSUME_FAILED)
                .min(Comparator.comparing(MessageDelivery::sentAt));
        if (consumeFailed.isPresent()) {
            MessageDelivery message = consumeFailed.get();
            return Optional.of(result(
                    facts,
                    DiagnosisStage.MESSAGE_DELIVERY,
                    DiagnosisRuleId.MESSAGE_CONSUME_FAILED,
                    "Message consume failed: " + message.lastError(),
                    List.of(messageEvidence(message, messageStatusSummary(message), message.sentAt()))));
        }

        return Optional.empty();
    }

    private Optional<DiagnosisResult> diagnoseCompensation(CollectedFacts facts) {
        Optional<CompensationTask> exhausted = facts.compensations().stream()
                .filter(task -> task.status() == CompensationStatus.RETRIES_EXHAUSTED)
                .min(Comparator.comparing(DeterministicDiagnosisRules::compensationObservedAt));
        if (exhausted.isPresent()) {
            CompensationTask task = exhausted.get();
            return Optional.of(result(
                    facts,
                    DiagnosisStage.COMPENSATION,
                    DiagnosisRuleId.COMPENSATION_RETRIES_EXHAUSTED,
                    "Compensation retries exhausted: " + task.lastError(),
                    List.of(compensationEvidence(task, compensationStatusSummary(task), compensationObservedAt(task)))));
        }

        Optional<CompensationTask> failed = facts.compensations().stream()
                .filter(task -> task.status() == CompensationStatus.FAILED)
                .min(Comparator.comparing(DeterministicDiagnosisRules::compensationObservedAt));
        if (failed.isPresent()) {
            CompensationTask task = failed.get();
            return Optional.of(result(
                    facts,
                    DiagnosisStage.COMPENSATION,
                    DiagnosisRuleId.COMPENSATION_FAILED,
                    "Compensation failed and may be retried: " + task.lastError(),
                    List.of(compensationEvidence(task, compensationStatusSummary(task), compensationObservedAt(task)))));
        }

        if (!facts.compensations().isEmpty() || !compensationExpected(facts)) {
            return Optional.empty();
        }

        PaymentTransaction triggerPayment = compensationTriggerPayment(facts).orElseThrow();
        return Optional.of(result(
                facts,
                DiagnosisStage.COMPENSATION,
                DiagnosisRuleId.COMPENSATION_NOT_CREATED,
                "Order is cancelled after a successful paid callback and no compensation task was found",
                List.of(
                        orderEvidence(facts.order(), orderStatusSummary(facts.order())),
                        paymentEvidence(triggerPayment, paymentStatusSummary(triggerPayment), triggerPayment.callbackReceivedAt()))));

    }

    private static boolean olderThan(Instant factAt, Instant observedAt, Duration threshold) {
        return factAt.plus(threshold).isBefore(observedAt);
    }

    private static boolean downstreamUpdateExpected(CollectedFacts facts) {
        return !successfulPayments(facts).isEmpty() && orderHasSuccessfulPaymentUpdate(facts.order().status());
    }

    private static boolean orderHasSuccessfulPaymentUpdate(OrderStatus status) {
        return status == OrderStatus.PAID
                || status == OrderStatus.OUTBOUND
                || status == OrderStatus.SHIPPED
                || status == OrderStatus.SIGNED
                || status == OrderStatus.COMPLETED
                || status == OrderStatus.CLOSED;
    }

    private static List<PaymentTransaction> successfulPayments(CollectedFacts facts) {
        return facts.payments().stream()
                .filter(payment -> payment.status() == PaymentStatus.CALLBACK_RECEIVED)
                .toList();
    }

    private static boolean compensationExpected(CollectedFacts facts) {
        return facts.order().status() == OrderStatus.CANCELLED
                && compensationTriggerPayment(facts).isPresent();
    }

    private static Optional<PaymentTransaction> compensationTriggerPayment(CollectedFacts facts) {
        return facts.payments().stream()
                .filter(payment -> payment.status() == PaymentStatus.CALLBACK_RECEIVED)
                .min(Comparator.comparing(PaymentTransaction::callbackReceivedAt));
    }

    private static boolean completeChain(CollectedFacts facts) {
        return !successfulPayments(facts).isEmpty()
                && facts.messages().stream().anyMatch(message -> message.status() == MessageDeliveryStatus.CONSUMED)
                && facts.compensations().stream().noneMatch(task -> task.status() == CompensationStatus.FAILED
                        || task.status() == CompensationStatus.RETRIES_EXHAUSTED)
                && facts.trace().map(TraceSummary::complete).orElse(false)
                && orderHasSuccessfulPaymentUpdate(facts.order().status());
    }

    private static DiagnosisResult result(
            CollectedFacts facts,
            DiagnosisStage stage,
            DiagnosisRuleId ruleId,
            String summary,
            List<DiagnosisEvidence> evidence) {
        return new DiagnosisResult(facts.order().orderId(), facts.dataMode(), stage, ruleId, summary, evidence, facts.warnings());
    }

    private static DiagnosisEvidence orderEvidence(OrderSnapshot order, String summary) {
        return new DiagnosisEvidence(
                "order:" + order.orderId().value(),
                "order",
                summary,
                order.stateChangedAt() == null ? order.updatedAt() : order.stateChangedAt());
    }

    private static String orderStatusSummary(OrderSnapshot order) {
        return "order status=" + order.status();
    }

    private static DiagnosisEvidence paymentEvidence(PaymentTransaction payment, String summary, Instant observedAt) {
        return new DiagnosisEvidence(
                "payment:" + payment.transactionId(),
                "payment",
                summary,
                observedAt);
    }

    private static String paymentStatusSummary(PaymentTransaction payment) {
        String summary = "payment status=" + payment.status() + ", requestedAt=" + payment.requestedAt();
        if (payment.providerCompletedAt() != null) {
            summary += ", providerCompletedAt=" + payment.providerCompletedAt();
        }
        if (payment.callbackReceivedAt() != null) {
            summary += ", callbackReceivedAt=" + payment.callbackReceivedAt();
        }
        if (payment.providerErrorCode() != null) {
            summary += ", providerErrorCode=" + payment.providerErrorCode()
                    + ", providerErrorSummary=" + payment.providerErrorSummary();
        }
        return summary;
    }

    private static DiagnosisEvidence messageEvidence(MessageDelivery message, String summary, Instant observedAt) {
        return new DiagnosisEvidence(
                "message:" + message.deliveryId(),
                "message",
                summary,
                observedAt);
    }

    private static String messageStatusSummary(MessageDelivery message) {
        String summary = "message status=" + message.status() + ", createdAt=" + message.createdAt();
        if (message.sentAt() != null) {
            summary += ", sentAt=" + message.sentAt();
        }
        if (message.consumedAt() != null) {
            summary += ", consumedAt=" + message.consumedAt();
        }
        if (message.lastError() != null) {
            summary += ", lastError=" + message.lastError();
        }
        return summary;
    }

    private static DiagnosisEvidence compensationEvidence(CompensationTask task, String summary, Instant observedAt) {
        return new DiagnosisEvidence(
                "compensation:" + task.taskId(),
                "compensation",
                summary,
                observedAt);
    }

    private static String compensationStatusSummary(CompensationTask task) {
        String summary = "compensation status=" + task.status()
                + ", retryCount=" + task.retryCount()
                + ", maxRetries=" + task.maxRetries()
                + ", createdAt=" + task.createdAt();
        if (task.lastAttemptAt() != null) {
            summary += ", lastAttemptAt=" + task.lastAttemptAt();
        }
        if (task.lastError() != null) {
            summary += ", lastError=" + task.lastError();
        }
        return summary;
    }

    private static DiagnosisEvidence traceEvidence(TraceSummary trace, String summary, Instant observedAt) {
        return new DiagnosisEvidence(
                "trace:" + trace.traceId(),
                "trace",
                summary,
                observedAt);
    }

    private static String traceStatusSummary(TraceSummary trace) {
        String summary = "trace complete=" + trace.complete() + ", startedAt=" + trace.startedAt();
        if (trace.endedAt() != null) {
            summary += ", endedAt=" + trace.endedAt();
        }
        return summary;
    }

    private static Instant paymentSuccessAt(PaymentTransaction payment) {
        return payment.callbackReceivedAt() == null ? payment.providerCompletedAt() : payment.callbackReceivedAt();
    }

    private static Instant paymentFailureAt(PaymentTransaction payment) {
        return payment.providerCompletedAt() == null ? payment.requestedAt() : payment.providerCompletedAt();
    }

    private static Instant traceObservedAt(TraceSummary trace) {
        return trace.endedAt() == null ? trace.startedAt() : trace.endedAt();
    }

    private static Instant compensationObservedAt(CompensationTask task) {
        return task.lastAttemptAt() == null ? task.createdAt() : task.lastAttemptAt();
    }
}
