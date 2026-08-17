package com.leecardo.paymentdiagnostics.application.diagnosis;

import java.time.Duration;
import java.util.Objects;

public record DiagnosisPolicy(Duration paymentProcessingTimeout, Duration messageConsumptionTimeout) {

    public DiagnosisPolicy {
        Objects.requireNonNull(paymentProcessingTimeout, "paymentProcessingTimeout must not be null");
        Objects.requireNonNull(messageConsumptionTimeout, "messageConsumptionTimeout must not be null");
        requirePositive(paymentProcessingTimeout, "paymentProcessingTimeout");
        requirePositive(messageConsumptionTimeout, "messageConsumptionTimeout");
    }

    private static void requirePositive(Duration duration, String fieldName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
