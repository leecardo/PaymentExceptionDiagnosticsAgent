package com.leecardo.paymentdiagnostics.domain;

/**
 * 消息投递状态词表，描述订单事件从待发送到消费成功或失败的投递生命周期。
 */
public enum MessageDeliveryStatus {
    /** 待发送，尚未产生发送或消费时间。 */
    PENDING,
    /** 已发送到消息系统，尚未被消费。 */
    SENT,
    /** 发送失败，必须记录最近一次错误。 */
    SEND_FAILED,
    /** 已被下游成功消费。 */
    CONSUMED,
    /** 已发送但消费失败，必须记录消费错误。 */
    CONSUME_FAILED
}
