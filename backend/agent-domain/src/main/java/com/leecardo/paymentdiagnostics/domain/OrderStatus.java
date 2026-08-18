package com.leecardo.paymentdiagnostics.domain;

/**
 * 订单状态词表，对应生产表 {@code prod_order_user.ORDER_STATE}。
 * <p>用于把订单库中的数字状态映射为诊断规则可读的领域状态。</p>
 */
public enum OrderStatus {
    /** 0：待支付。 */
    PENDING_PAYMENT,
    /** 1：已取消。 */
    CANCELLED,
    /** 2：已支付。 */
    PAID,
    /** 3：出库中或已出库。 */
    OUTBOUND,
    /** 4：已发货。 */
    SHIPPED,
    /** 5：已签收。 */
    SIGNED,
    /** 6：交易完成。 */
    COMPLETED,
    /** 7：交易关闭。 */
    CLOSED
}
