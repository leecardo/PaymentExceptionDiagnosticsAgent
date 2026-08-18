package com.leecardo.paymentdiagnostics.domain;

/**
 * 支付流水状态词表，描述从发起支付到渠道回调或失败的诊断状态。
 */
public enum PaymentStatus {
    /** 已发起支付请求，尚未进入渠道处理完成态。 */
    REQUESTED,
    /** 渠道处理中，尚未获得明确成功、回调或失败结果。 */
    PROCESSING,
    /** 支付渠道已返回成功，但系统尚未收到或处理成功回调。 */
    PROVIDER_SUCCEEDED,
    /** 系统已收到支付成功回调。 */
    CALLBACK_RECEIVED,
    /** 支付失败，必须携带渠道错误码和摘要。 */
    FAILED
}
