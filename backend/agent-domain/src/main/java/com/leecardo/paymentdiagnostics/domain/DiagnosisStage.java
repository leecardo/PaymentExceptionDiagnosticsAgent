package com.leecardo.paymentdiagnostics.domain;

/**
 * 诊断阶段词表，表示支付异常规则把证据归因到的支付业务流程位置。
 * <p>阶段从订单创建、支付请求、回调、订单状态更新、消息投递、补偿到链路关联，
 * 用于对诊断结果进行粗粒度分类。</p>
 */
public enum DiagnosisStage {
    /** 订单已创建阶段，诊断从订单基础事实开始。 */
    ORDER_CREATED,
    /** 已发起支付请求阶段。 */
    PAYMENT_REQUESTED,
    /** 支付渠道确认成功阶段。 */
    PAYMENT_CONFIRMED,
    /** 支付回调处理阶段。 */
    PAYMENT_CALLBACK,
    /** 支付成功后订单状态更新阶段。 */
    ORDER_STATE_UPDATE,
    /** 订单事件消息发送与消费阶段。 */
    MESSAGE_DELIVERY,
    /** 异常补偿任务创建、执行或重试阶段。 */
    COMPENSATION,
    /** 调用链路与业务关联标识核对阶段。 */
    TRACE_CORRELATION,
    /** 全流程已完成且未发现已知异常。 */
    COMPLETED,
    /** 证据不足，无法定位到明确异常阶段。 */
    INSUFFICIENT_EVIDENCE
}
