package com.leecardo.paymentdiagnostics.infrastructure.simulation;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.leecardo.paymentdiagnostics.application.port.FactQueryException;
import com.leecardo.paymentdiagnostics.domain.CompensationStatus;
import com.leecardo.paymentdiagnostics.domain.CompensationTask;
import com.leecardo.paymentdiagnostics.domain.MessageDelivery;
import com.leecardo.paymentdiagnostics.domain.MessageDeliveryStatus;
import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.OrderRole;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;
import com.leecardo.paymentdiagnostics.domain.OrderStatus;
import com.leecardo.paymentdiagnostics.domain.PaymentStatus;
import com.leecardo.paymentdiagnostics.domain.PaymentTransaction;
import com.leecardo.paymentdiagnostics.domain.TraceSummary;
import org.springframework.core.io.Resource;

/**
 * JSON 仿真场景加载器。
 * <p>
 * 从 Spring {@link Resource} 读取场景文件，先反序列化为内部 DTO，再通过领域构造器映射为
 * {@link SimulationScenarioDocument}；加载失败时保留逻辑对象 ID，避免把绝对文件路径暴露到错误信息中。
 */
public final class SimulationScenarioLoader {

    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("[A-Za-z]:[/\\\\][^\\]\\s]+[/\\\\]");

    private final ObjectMapper objectMapper;

    /**
     * 复制调用方提供的 {@link ObjectMapper}，并注册 {@link JavaTimeModule} 以支持 {@link Instant} 字段解析。
     */
    public SimulationScenarioLoader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null")
                .copy()
                .registerModule(new JavaTimeModule());
    }

    /**
     * 读取场景资源并转换为领域化场景文档；所有 IO、JSON 与领域校验异常都会包装为启动期异常。
     */
    public SimulationScenarioDocument load(Resource resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        String resourceDescription = safeResourceDescription(resource.getDescription());
        try (InputStream input = resource.getInputStream()) {
            DocumentDto dto = objectMapper.readValue(input, DocumentDto.class);
            return dto.toDocument();
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("Failed to load simulation scenario " + resourceDescription + logicalId(ex) + ": " + safeMessage(ex));
        }
    }

    /**
     * 从异常链中提取 DTO 映射失败时记录的逻辑对象 ID，用于定位坏数据而不依赖文件路径。
     */
    private static String logicalId(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ObjectMappingException objectMappingException) {
                return " objectId " + objectMappingException.objectId();
            }
            current = current.getCause();
        }
        return "";
    }

    /**
     * 返回经过路径脱敏的错误消息；空消息退化为异常类型名。
     */
    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return sanitizePath(message.strip());
    }

    /**
     * 返回经过路径脱敏的资源描述，避免启动失败日志泄露本机绝对路径。
     */
    private static String safeResourceDescription(String description) {
        if (description == null || description.isBlank()) {
            return "unknown resource";
        }
        return sanitizePath(description.strip());
    }

    /**
     * 删除 Linux、macOS 与 Windows 风格的绝对路径片段，仅保留文件名或业务错误内容。
     */
    private static String sanitizePath(String value) {
        String sanitized = value.replace('\\', '/');
        sanitized = sanitized.replaceAll("file \\[.*/([^/\\\\]]+)\\]", "file [$1]");
        sanitized = sanitized.replaceAll(".*/([^/\\s:]+)", "$1");
        return WINDOWS_ABSOLUTE_PATH.matcher(sanitized).replaceAll("");
    }

    /**
     * 场景文档 DTO，字段与 JSON schema 对齐，并负责把五类事实 DTO 批量映射到领域对象。
     */
    private record DocumentDto(
            Integer schemaVersion,
            Instant observedAt,
            List<OrderDto> orders,
            List<PaymentTransactionDto> paymentTransactions,
            List<MessageDeliveryDto> messageDeliveries,
            List<CompensationTaskDto> compensationTasks,
            List<TraceSummaryDto> traceSummaries,
            List<FailureDto> failures) {

        /**
         * 校验必填顶层字段，并调用各事实 DTO 的 {@code toDomain()} 构造领域场景文档。
         */
        SimulationScenarioDocument toDocument() {
            return new SimulationScenarioDocument(
                    requireNonNull(schemaVersion, "schemaVersion"),
                    requireNonNull(observedAt, "observedAt"),
                    mapRequired(orders, "orders", OrderDto::toDomain),
                    mapRequired(paymentTransactions, "paymentTransactions", PaymentTransactionDto::toDomain),
                    mapRequired(messageDeliveries, "messageDeliveries", MessageDeliveryDto::toDomain),
                    mapRequired(compensationTasks, "compensationTasks", CompensationTaskDto::toDomain),
                    mapRequired(traceSummaries, "traceSummaries", TraceSummaryDto::toDomain),
                    mapRequired(failures, "failures", FailureDto::toDomain));
        }
    }

    /**
     * 订单事实 DTO，负责把 JSON 字段映射到 {@link OrderSnapshot} 构造参数。
     */
    private record OrderDto(
            String orderId,
            String masterOrderId,
            String role,
            String productId,
            String productName,
            String productType,
            Integer goodsCount,
            BigDecimal unitPrice,
            BigDecimal orderAmount,
            BigDecimal paymentAmount,
            String paymentSource,
            String providerOrderId,
            String orderSource,
            String status,
            Instant orderedAt,
            Instant stateChangedAt,
            Instant createdAt,
            Instant updatedAt) {

        /**
         * 构造订单快照领域对象；校验失败时用订单号包装为 {@link ObjectMappingException}。
         */
        OrderSnapshot toDomain() {
            try {
                return new OrderSnapshot(
                        toOrderId(orderId),
                        masterOrderId == null ? null : toOrderId(masterOrderId),
                        parseEnum(OrderRole.class, role, "role"),
                        productId,
                        productName,
                        productType,
                        requireNonNull(goodsCount, "goodsCount"),
                        unitPrice,
                        orderAmount,
                        paymentAmount,
                        paymentSource,
                        providerOrderId,
                        orderSource,
                        parseEnum(OrderStatus.class, status, "status"),
                        orderedAt,
                        stateChangedAt,
                        createdAt,
                        updatedAt);
            } catch (RuntimeException ex) {
                throw new ObjectMappingException(objectId(orderId, "order"), ex);
            }
        }
    }

    /**
     * 支付交易 DTO，负责把 JSON 字段映射到 {@link PaymentTransaction} 构造参数。
     */
    private record PaymentTransactionDto(
            String transactionId,
            String orderId,
            String provider,
            BigDecimal amount,
            String status,
            Instant requestedAt,
            Instant providerCompletedAt,
            Instant callbackReceivedAt,
            String providerErrorCode,
            String providerErrorSummary) {

        /**
         * 构造支付交易领域对象；校验失败时使用交易号作为逻辑对象 ID。
         */
        PaymentTransaction toDomain() {
            try {
                return new PaymentTransaction(
                        transactionId,
                        toOrderId(orderId),
                        provider,
                        amount,
                        parseEnum(PaymentStatus.class, status, "status"),
                        requestedAt,
                        providerCompletedAt,
                        callbackReceivedAt,
                        providerErrorCode,
                        providerErrorSummary);
            } catch (RuntimeException ex) {
                throw new ObjectMappingException(objectId(transactionId, "payment"), ex);
            }
        }
    }

    /**
     * 消息投递 DTO，负责把 JSON 字段映射到 {@link MessageDelivery} 构造参数。
     */
    private record MessageDeliveryDto(
            String deliveryId,
            String orderId,
            String eventType,
            String correlationId,
            String status,
            Instant createdAt,
            Instant sentAt,
            Instant consumedAt,
            String lastError) {

        /**
         * 构造消息投递领域对象；校验失败时使用投递号作为逻辑对象 ID。
         */
        MessageDelivery toDomain() {
            try {
                return new MessageDelivery(
                        deliveryId,
                        toOrderId(orderId),
                        eventType,
                        correlationId,
                        parseEnum(MessageDeliveryStatus.class, status, "status"),
                        createdAt,
                        sentAt,
                        consumedAt,
                        lastError);
            } catch (RuntimeException ex) {
                throw new ObjectMappingException(objectId(deliveryId, "message"), ex);
            }
        }
    }

    /**
     * 补偿任务 DTO，负责把 JSON 字段映射到 {@link CompensationTask} 构造参数。
     */
    private record CompensationTaskDto(
            String taskId,
            String orderId,
            String action,
            String status,
            Integer retryCount,
            Integer maxRetries,
            Instant createdAt,
            Instant lastAttemptAt,
            String lastError) {

        /**
         * 构造补偿任务领域对象；校验失败时使用任务号作为逻辑对象 ID。
         */
        CompensationTask toDomain() {
            try {
                return new CompensationTask(
                        taskId,
                        toOrderId(orderId),
                        action,
                        parseEnum(CompensationStatus.class, status, "status"),
                        requireNonNull(retryCount, "retryCount"),
                        requireNonNull(maxRetries, "maxRetries"),
                        createdAt,
                        lastAttemptAt,
                        lastError);
            } catch (RuntimeException ex) {
                throw new ObjectMappingException(objectId(taskId, "compensation"), ex);
            }
        }
    }

    /**
     * 链路摘要 DTO，负责把 JSON 字段映射到 {@link TraceSummary} 构造参数。
     */
    private record TraceSummaryDto(
            String traceId,
            String orderId,
            String correlationId,
            Instant startedAt,
            Instant endedAt,
            Boolean complete,
            String summary) {

        /**
         * 构造链路摘要领域对象；校验失败时使用 traceId 作为逻辑对象 ID。
         */
        TraceSummary toDomain() {
            try {
                return new TraceSummary(
                        traceId,
                        toOrderId(orderId),
                        correlationId,
                        startedAt,
                        endedAt,
                        requireNonNull(complete, "complete"),
                        summary);
            } catch (RuntimeException ex) {
                throw new ObjectMappingException(objectId(traceId, "trace"), ex);
            }
        }
    }

    /**
     * 故障注入 DTO，负责把字符串事实源和异常类型映射为故障记录。
     */
    private record FailureDto(String source, String orderId, String kind) {

        /**
         * 构造故障记录领域对象；校验失败时使用订单号作为逻辑对象 ID。
         */
        SimulationScenarioDocument.FailureRecord toDomain() {
            try {
                return new SimulationScenarioDocument.FailureRecord(source, orderId, kind);
            } catch (RuntimeException ex) {
                throw new ObjectMappingException(objectId(orderId, "failure"), ex);
            }
        }
    }

    /**
     * 将场景中的订单号字符串转换为领域值对象，由领域构造器执行格式校验。
     */
    private static OrderId toOrderId(String value) {
        return new OrderId(value);
    }

    /**
     * 校验 DTO 必填字段非空，并保留字段名用于错误定位。
     */
    private static <T> T requireNonNull(T value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " must not be null");
    }

    /**
     * 解析字符串枚举值；非法值会报告当前字段允许的枚举集合。
     */
    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        try {
            return Enum.valueOf(enumType, value.strip());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + " must be one of " + List.of(enumType.getEnumConstants()), ex);
        }
    }

    /**
     * 映射必填 DTO 列表，逐项触发领域构造与对象级异常包装。
     */
    private static <T, R> List<R> mapRequired(List<T> values, String fieldName, Mapper<T, R> mapper) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        return values.stream().map(mapper::map).toList();
    }

    /**
     * 选择错误提示中的逻辑对象 ID；缺少业务 ID 时使用事实类型兜底。
     */
    private static String objectId(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.strip();
    }

    /**
     * DTO 到领域对象的映射函数接口，用于统一处理五类事实列表。
     */
    @FunctionalInterface
    private interface Mapper<T, R> {
        R map(T value);
    }

    /**
     * 包装单个 DTO 映射失败的领域校验异常，并携带脱敏后的逻辑对象 ID。
     */
    private static final class ObjectMappingException extends RuntimeException {

        private final String objectId;

        /**
         * 使用原始校验异常作为 cause，同时保留定位问题数据的逻辑对象 ID。
         */
        private ObjectMappingException(String objectId, RuntimeException cause) {
            super(cause.getMessage(), cause);
            this.objectId = objectId;
        }

        /**
         * 返回映射失败的逻辑对象 ID，供顶层加载异常拼接使用。
         */
        private String objectId() {
            return objectId;
        }
    }
}
