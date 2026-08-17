package com.leecardo.paymentdiagnostics.domain;

public enum OrderStatus {
    PENDING_PAYMENT,
    CANCELLED,
    PAID,
    OUTBOUND,
    SHIPPED,
    SIGNED,
    COMPLETED,
    CLOSED
}
