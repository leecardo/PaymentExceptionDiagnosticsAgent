package com.leecardo.paymentdiagnostics.application.order;

import java.util.Objects;

import com.leecardo.paymentdiagnostics.domain.OrderId;

public final class OrderNotFoundException extends RuntimeException {

    private final OrderId orderId;

    public OrderNotFoundException(OrderId orderId) {
        super("Order not found: " + Objects.requireNonNull(orderId, "orderId must not be null").value());
        this.orderId = orderId;
    }

    public OrderId orderId() {
        return orderId;
    }
}
