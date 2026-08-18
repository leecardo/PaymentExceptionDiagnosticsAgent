package com.leecardo.paymentdiagnostics.application.order;

import java.util.Objects;

import com.leecardo.paymentdiagnostics.domain.OrderId;

/**
 * 订单不存在异常，表示订单查询端口确认指定订单业务记录不存在。
 */
public final class OrderNotFoundException extends RuntimeException {

    private final OrderId orderId;

    /**
     * 创建订单不存在异常，并保留未命中的订单标识便于上层返回或审计。
     *
     * @param orderId 未找到的订单标识
     */
    public OrderNotFoundException(OrderId orderId) {
        super("Order not found: " + Objects.requireNonNull(orderId, "orderId must not be null").value());
        this.orderId = orderId;
    }

    /**
     * 返回未找到的订单标识。
     *
     * @return 订单标识
     */
    public OrderId orderId() {
        return orderId;
    }
}
