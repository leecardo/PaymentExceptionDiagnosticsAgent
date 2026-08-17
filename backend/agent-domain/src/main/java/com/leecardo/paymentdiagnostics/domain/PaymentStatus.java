package com.leecardo.paymentdiagnostics.domain;

public enum PaymentStatus {
    REQUESTED,
    PROCESSING,
    PROVIDER_SUCCEEDED,
    CALLBACK_RECEIVED,
    FAILED
}
