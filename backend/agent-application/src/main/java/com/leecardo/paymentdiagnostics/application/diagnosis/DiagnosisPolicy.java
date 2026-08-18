package com.leecardo.paymentdiagnostics.application.diagnosis;

import java.time.Duration;
import java.util.Objects;

/**
 * 诊断策略配置，定义确定性规则判断支付处理和消息消费超时所需的时间阈值。
 */
public record DiagnosisPolicy(Duration paymentProcessingTimeout, Duration messageConsumptionTimeout) {

    /**
     * 校验诊断策略阈值，两个超时阈值都必须为正数。
     *
     * @throws IllegalArgumentException 当任一阈值为零或负数时抛出
     */
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
