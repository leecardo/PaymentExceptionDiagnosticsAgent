package com.leecardo.paymentdiagnostics.api.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deterministic diagnosis thresholds sourced from application configuration.
 *
 * <p>诊断阈值配置属性，绑定前缀 {@code app.diagnosis}。默认支付处理中超时阈值为 15 分钟，
 * 消息消费超时阈值为 5 分钟，用于构建确定性诊断策略。</p>
 */
@ConfigurationProperties(prefix = "app.diagnosis")
public class DiagnosisProperties {

    private Duration paymentProcessingTimeout = Duration.ofMinutes(15);
    private Duration messageConsumptionTimeout = Duration.ofMinutes(5);

    /**
     * 读取支付处理中超时阈值，默认值为 15 分钟。
     *
     * @return 支付处理中超时阈值
     */
    public Duration getPaymentProcessingTimeout() {
        return paymentProcessingTimeout;
    }

    /**
     * 设置支付处理中超时阈值。
     *
     * @param paymentProcessingTimeout 支付处理中超时阈值
     */
    public void setPaymentProcessingTimeout(Duration paymentProcessingTimeout) {
        this.paymentProcessingTimeout = paymentProcessingTimeout;
    }

    /**
     * 读取消息消费超时阈值，默认值为 5 分钟。
     *
     * @return 消息消费超时阈值
     */
    public Duration getMessageConsumptionTimeout() {
        return messageConsumptionTimeout;
    }

    /**
     * 设置消息消费超时阈值。
     *
     * @param messageConsumptionTimeout 消息消费超时阈值
     */
    public void setMessageConsumptionTimeout(Duration messageConsumptionTimeout) {
        this.messageConsumptionTimeout = messageConsumptionTimeout;
    }
}
