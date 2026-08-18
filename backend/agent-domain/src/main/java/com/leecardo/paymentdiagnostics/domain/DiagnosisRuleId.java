package com.leecardo.paymentdiagnostics.domain;

/**
 * 诊断规则标识词表，稳定命名支付异常诊断引擎可输出的 15 类规则结果。
 * <p>用于跨应用层、接口层和测试数据引用具体规则，枚举名即规则的持久化与展示锚点。</p>
 */
public enum DiagnosisRuleId {
    /** 未发现已知异常。 */
    NO_KNOWN_EXCEPTION,
    /** 订单存在但支付尚未发起。 */
    PAYMENT_NOT_STARTED,
    /** 支付长时间处于处理中。 */
    PAYMENT_PROCESSING_TIMEOUT,
    /** 渠道成功但系统缺少支付回调。 */
    PROVIDER_SUCCEEDED_CALLBACK_MISSING,
    /** 支付回调成功但订单状态未更新。 */
    CALLBACK_SUCCEEDED_ORDER_NOT_UPDATED,
    /** 支付失败且渠道返回错误。 */
    PAYMENT_FAILED_WITH_PROVIDER_ERROR,
    /** 支付后订单事件消息未发送。 */
    MESSAGE_NOT_SENT,
    /** 订单事件消息发送失败。 */
    MESSAGE_SEND_FAILED,
    /** 订单事件消息未被消费。 */
    MESSAGE_NOT_CONSUMED,
    /** 订单事件消息消费失败。 */
    MESSAGE_CONSUME_FAILED,
    /** 需要补偿但补偿任务未创建。 */
    COMPENSATION_NOT_CREATED,
    /** 补偿任务执行失败。 */
    COMPENSATION_FAILED,
    /** 补偿任务重试次数已耗尽。 */
    COMPENSATION_RETRIES_EXHAUSTED,
    /** 诊断所需调用链路缺失。 */
    TRACE_MISSING,
    /** 证据不足，无法输出更具体规则。 */
    INSUFFICIENT_EVIDENCE
}
