package com.leecardo.paymentdiagnostics.domain;

/**
 * Coarse payment flow stages a future diagnosis may identify from evidence.
 */
public enum DiagnosisStage {
    ORDER_CREATED,
    PAYMENT_REQUESTED,
    PAYMENT_CONFIRMED,
    MESSAGE_DELIVERY,
    COMPENSATION,
    TRACE_CORRELATION,
    INSUFFICIENT_EVIDENCE
}
