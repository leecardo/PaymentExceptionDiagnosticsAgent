package com.leecardo.paymentdiagnostics.infrastructure.simulation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.leecardo.paymentdiagnostics.application.port.FactQueryException;
import com.leecardo.paymentdiagnostics.domain.CompensationTask;
import com.leecardo.paymentdiagnostics.domain.MessageDelivery;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;
import com.leecardo.paymentdiagnostics.domain.PaymentTransaction;
import com.leecardo.paymentdiagnostics.domain.TraceSummary;

/**
 * 仿真场景文档的领域化表示。
 * <p>
 * 文档 schema 固定为版本 1，包含订单、支付、消息、补偿、链路五类事实，
 * 以及用于模拟端口异常的故障记录集合。
 */
public record SimulationScenarioDocument(
        int schemaVersion,
        Instant observedAt,
        List<OrderSnapshot> orders,
        List<PaymentTransaction> paymentTransactions,
        List<MessageDelivery> messageDeliveries,
        List<CompensationTask> compensationTasks,
        List<TraceSummary> traceSummaries,
        List<FailureRecord> failures) {

    /**
     * 校验 schema 版本并把所有事实列表防御性复制为不可变列表。
     */
    public SimulationScenarioDocument {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("schemaVersion must be 1");
        }
        Objects.requireNonNull(observedAt, "observedAt must not be null");
        orders = copyRequired(orders, "orders");
        paymentTransactions = copyRequired(paymentTransactions, "paymentTransactions");
        messageDeliveries = copyRequired(messageDeliveries, "messageDeliveries");
        compensationTasks = copyRequired(compensationTasks, "compensationTasks");
        traceSummaries = copyRequired(traceSummaries, "traceSummaries");
        failures = copyRequired(failures, "failures");
    }

    /**
     * 要求列表字段存在，并复制为不可变列表以避免场景加载后被外部修改。
     */
    private static <T> List<T> copyRequired(List<T> values, String fieldName) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        return List.copyOf(values);
    }

    /**
     * 单条端口故障配置，表示某个事实源在指定订单上的查询异常类型。
     */
    public record FailureRecord(SimulationFactSource source, String orderId, FactQueryException.Kind kind) {

        /**
         * 从 JSON 字符串枚举值构造故障记录。
         */
        public FailureRecord(String source, String orderId, String kind) {
            this(parseEnum(SimulationFactSource.class, source, "source"), orderId, parseEnum(FactQueryException.Kind.class, kind, "kind"));
        }

        /**
         * 校验故障源、订单号和异常类型，并规范化订单号空白。
         */
        public FailureRecord {
            Objects.requireNonNull(source, "source must not be null");
            Objects.requireNonNull(orderId, "orderId must not be null");
            orderId = orderId.strip();
            if (orderId.isEmpty()) {
                throw new IllegalArgumentException("orderId must not be blank");
            }
            Objects.requireNonNull(kind, "kind must not be null");
        }
    }

    /**
     * 解析场景文件中的字符串枚举值，并在非法值中给出允许集合。
     */
    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        try {
            return Enum.valueOf(enumType, value.strip());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + " must be one of " + List.of(enumType.getEnumConstants()), ex);
        }
    }
}
