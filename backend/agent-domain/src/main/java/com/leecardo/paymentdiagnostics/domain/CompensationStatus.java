package com.leecardo.paymentdiagnostics.domain;

/**
 * 补偿任务状态词表，描述支付异常诊断中自动修复或重试任务的生命周期。
 */
public enum CompensationStatus {
    /** 待执行，尚未开始补偿动作。 */
    PENDING,
    /** 正在执行补偿动作。 */
    RUNNING,
    /** 补偿动作执行成功。 */
    SUCCEEDED,
    /** 补偿动作失败但仍可能继续重试。 */
    FAILED,
    /** 重试次数已耗尽，任务终止且必须记录最后错误。 */
    RETRIES_EXHAUSTED
}
