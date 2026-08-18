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

/**
 * 确定性诊断规则引擎，基于已收集事实按优先级返回第一个可解释的支付异常结论。
 *
 * <p>整体诊断流程固定为 payment rules -> message rules -> compensation rules -> trace gap -> normal/insufficient。
 * 支付规则优先级高于消息和补偿规则，避免在支付尚未完成或支付回调异常时误报下游链路问题。
 *
 * <p>诊断证据 ID 采用来源前缀命名：order:{orderId}、payment:{transactionId}、message:{deliveryId}、
 * compensation:{taskId}、trace:{traceId}，便于上层展示和追溯原始事实。
 */
public final class DeterministicDiagnosisRules {

    private final DiagnosisPolicy policy;

    /**
     * 创建规则引擎并注入诊断策略阈值。
     *
     * @param policy 支付处理和消息消费超时策略
     */
    public DeterministicDiagnosisRules(DiagnosisPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    /**
     * 执行确定性诊断，按支付、消息、补偿、Trace 缺口、正常/证据不足的顺序短路匹配。
     *
     * <p>一旦支付规则命中即直接返回，不再检查消息或补偿规则；该优先级体现支付链路是后续消息与补偿的前置事实。
     *
     * @param facts 已收集的诊断事实集合
     * @return 诊断结果
     */
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

    /**
     * 检查支付阶段规则：未发起支付、支付处理中超时、支付渠道成功但回调缺失、回调成功但订单未更新、渠道失败。
     *
     * @param facts 已收集的诊断事实集合
     * @return 命中的支付诊断结果；未命中时为空
     */
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

    /**
     * 检查消息阶段规则：应发送但未发送、发送失败、已发送但消费超时、消费失败。
     *
     * @param facts 已收集的诊断事实集合
     * @return 命中的消息诊断结果；未命中时为空
     */
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

    /**
     * 检查补偿阶段规则：重试耗尽、补偿失败、订单取消且支付成功后应创建补偿但未创建。
     *
     * @param facts 已收集的诊断事实集合
     * @return 命中的补偿诊断结果；未命中时为空
     */
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

    /**
     * 判断事实时间是否严格早于观察时间减去阈值。
     *
     * <p>边界语义为 age == threshold 不算超时，只有 age > threshold 才算超时。
     */
    private static boolean olderThan(Instant factAt, Instant observedAt, Duration threshold) {
        return factAt.plus(threshold).isBefore(observedAt);
    }

    /**
     * 判断成功支付后的订单状态是否已经进入需要发送下游消息的阶段。
     */
    private static boolean downstreamUpdateExpected(CollectedFacts facts) {
        return !successfulPayments(facts).isEmpty() && orderHasSuccessfulPaymentUpdate(facts.order().status());
    }

    /**
     * 判断订单状态是否体现支付成功后的业务更新。
     */
    private static boolean orderHasSuccessfulPaymentUpdate(OrderStatus status) {
        return status == OrderStatus.PAID
                || status == OrderStatus.OUTBOUND
                || status == OrderStatus.SHIPPED
                || status == OrderStatus.SIGNED
                || status == OrderStatus.COMPLETED
                || status == OrderStatus.CLOSED;
    }

    /**
     * 提取已收到成功回调的支付流水，作为消息和补偿规则的前置事实。
     */
    private static List<PaymentTransaction> successfulPayments(CollectedFacts facts) {
        return facts.payments().stream()
                .filter(payment -> payment.status() == PaymentStatus.CALLBACK_RECEIVED)
                .toList();
    }

    /**
     * 判断取消订单是否需要存在补偿任务。
     */
    private static boolean compensationExpected(CollectedFacts facts) {
        return facts.order().status() == OrderStatus.CANCELLED
                && compensationTriggerPayment(facts).isPresent();
    }

    /**
     * 选择最早收到成功回调的支付流水作为补偿触发证据。
     */
    private static Optional<PaymentTransaction> compensationTriggerPayment(CollectedFacts facts) {
        return facts.payments().stream()
                .filter(payment -> payment.status() == PaymentStatus.CALLBACK_RECEIVED)
                .min(Comparator.comparing(PaymentTransaction::callbackReceivedAt));
    }

    /**
     * 判断支付、订单、消息、补偿和 Trace 是否构成完整且无已知异常的链路。
     */
    private static boolean completeChain(CollectedFacts facts) {
        return !successfulPayments(facts).isEmpty()
                && facts.messages().stream().anyMatch(message -> message.status() == MessageDeliveryStatus.CONSUMED)
                && facts.compensations().stream().noneMatch(task -> task.status() == CompensationStatus.FAILED
                        || task.status() == CompensationStatus.RETRIES_EXHAUSTED)
                && facts.trace().map(TraceSummary::complete).orElse(false)
                && orderHasSuccessfulPaymentUpdate(facts.order().status());
    }

    /**
     * 组装诊断结果并透传数据模式、阶段、规则、证据和收集警告。
     */
    private static DiagnosisResult result(
            CollectedFacts facts,
            DiagnosisStage stage,
            DiagnosisRuleId ruleId,
            String summary,
            List<DiagnosisEvidence> evidence) {
        return new DiagnosisResult(facts.order().orderId(), facts.dataMode(), stage, ruleId, summary, evidence, facts.warnings());
    }

    /**
     * 构造订单证据，ID 采用 order:{orderId}。
     */
    private static DiagnosisEvidence orderEvidence(OrderSnapshot order, String summary) {
        return new DiagnosisEvidence(
                "order:" + order.orderId().value(),
                "order",
                summary,
                order.stateChangedAt() == null ? order.updatedAt() : order.stateChangedAt());
    }

    /**
     * 生成订单状态摘要。
     */
    private static String orderStatusSummary(OrderSnapshot order) {
        return "order status=" + order.status();
    }

    /**
     * 构造支付证据，ID 采用 payment:{transactionId}。
     */
    private static DiagnosisEvidence paymentEvidence(PaymentTransaction payment, String summary, Instant observedAt) {
        return new DiagnosisEvidence(
                "payment:" + payment.transactionId(),
                "payment",
                summary,
                observedAt);
    }

    /**
     * 生成支付流水状态摘要，包含可用的渠道完成、回调和错误信息。
     */
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

    /**
     * 构造消息投递证据，ID 采用 message:{deliveryId}。
     */
    private static DiagnosisEvidence messageEvidence(MessageDelivery message, String summary, Instant observedAt) {
        return new DiagnosisEvidence(
                "message:" + message.deliveryId(),
                "message",
                summary,
                observedAt);
    }

    /**
     * 生成消息投递状态摘要，包含创建、发送、消费和错误信息。
     */
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

    /**
     * 构造补偿任务证据，ID 采用 compensation:{taskId}。
     */
    private static DiagnosisEvidence compensationEvidence(CompensationTask task, String summary, Instant observedAt) {
        return new DiagnosisEvidence(
                "compensation:" + task.taskId(),
                "compensation",
                summary,
                observedAt);
    }

    /**
     * 生成补偿任务状态摘要，包含重试次数、最近尝试和错误信息。
     */
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

    /**
     * 构造 Trace 证据，ID 采用 trace:{traceId}。
     */
    private static DiagnosisEvidence traceEvidence(TraceSummary trace, String summary, Instant observedAt) {
        return new DiagnosisEvidence(
                "trace:" + trace.traceId(),
                "trace",
                summary,
                observedAt);
    }

    /**
     * 生成 Trace 完整性摘要。
     */
    private static String traceStatusSummary(TraceSummary trace) {
        String summary = "trace complete=" + trace.complete() + ", startedAt=" + trace.startedAt();
        if (trace.endedAt() != null) {
            summary += ", endedAt=" + trace.endedAt();
        }
        return summary;
    }

    /**
     * 取支付成功时间，优先使用回调接收时间；没有回调时使用渠道完成时间。
     */
    private static Instant paymentSuccessAt(PaymentTransaction payment) {
        return payment.callbackReceivedAt() == null ? payment.providerCompletedAt() : payment.callbackReceivedAt();
    }

    /**
     * 取支付失败时间，优先使用渠道完成时间；缺失时退回支付请求时间。
     */
    private static Instant paymentFailureAt(PaymentTransaction payment) {
        return payment.providerCompletedAt() == null ? payment.requestedAt() : payment.providerCompletedAt();
    }

    /**
     * 取 Trace 观察时间，优先使用结束时间；未结束时使用开始时间。
     */
    private static Instant traceObservedAt(TraceSummary trace) {
        return trace.endedAt() == null ? trace.startedAt() : trace.endedAt();
    }

    /**
     * 取补偿任务观察时间，优先使用最近尝试时间；尚未尝试时使用创建时间。
     */
    private static Instant compensationObservedAt(CompensationTask task) {
        return task.lastAttemptAt() == null ? task.createdAt() : task.lastAttemptAt();
    }
}
