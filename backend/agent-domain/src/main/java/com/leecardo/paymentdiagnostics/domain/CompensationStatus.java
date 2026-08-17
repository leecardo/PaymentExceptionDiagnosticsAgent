package com.leecardo.paymentdiagnostics.domain;

public enum CompensationStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    RETRIES_EXHAUSTED
}
