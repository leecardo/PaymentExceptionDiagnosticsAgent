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

public final class SimulationScenarioLoader {

    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("[A-Za-z]:[/\\\\][^\\]\\s]+[/\\\\]");

    private final ObjectMapper objectMapper;

    public SimulationScenarioLoader(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null")
                .copy()
                .registerModule(new JavaTimeModule());
    }

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

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getSimpleName();
        }
        return sanitizePath(message.strip());
    }

    private static String safeResourceDescription(String description) {
        if (description == null || description.isBlank()) {
            return "unknown resource";
        }
        return sanitizePath(description.strip());
    }

    private static String sanitizePath(String value) {
        String sanitized = value.replace('\\', '/');
        sanitized = sanitized.replaceAll("file \\[.*/([^/\\\\]]+)\\]", "file [$1]");
        sanitized = sanitized.replaceAll(".*/([^/\\s:]+)", "$1");
        return WINDOWS_ABSOLUTE_PATH.matcher(sanitized).replaceAll("");
    }

    private record DocumentDto(
            Integer schemaVersion,
            Instant observedAt,
            List<OrderDto> orders,
            List<PaymentTransactionDto> paymentTransactions,
            List<MessageDeliveryDto> messageDeliveries,
            List<CompensationTaskDto> compensationTasks,
            List<TraceSummaryDto> traceSummaries,
            List<FailureDto> failures) {

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

    private record TraceSummaryDto(
            String traceId,
            String orderId,
            String correlationId,
            Instant startedAt,
            Instant endedAt,
            Boolean complete,
            String summary) {

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

    private record FailureDto(String source, String orderId, String kind) {

        SimulationScenarioDocument.FailureRecord toDomain() {
            try {
                return new SimulationScenarioDocument.FailureRecord(source, orderId, kind);
            } catch (RuntimeException ex) {
                throw new ObjectMappingException(objectId(orderId, "failure"), ex);
            }
        }
    }

    private static OrderId toOrderId(String value) {
        return new OrderId(value);
    }

    private static <T> T requireNonNull(T value, String fieldName) {
        return Objects.requireNonNull(value, fieldName + " must not be null");
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        try {
            return Enum.valueOf(enumType, value.strip());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(fieldName + " must be one of " + List.of(enumType.getEnumConstants()), ex);
        }
    }

    private static <T, R> List<R> mapRequired(List<T> values, String fieldName, Mapper<T, R> mapper) {
        Objects.requireNonNull(values, fieldName + " must not be null");
        return values.stream().map(mapper::map).toList();
    }

    private static String objectId(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.strip();
    }

    @FunctionalInterface
    private interface Mapper<T, R> {
        R map(T value);
    }

    private static final class ObjectMappingException extends RuntimeException {

        private final String objectId;

        private ObjectMappingException(String objectId, RuntimeException cause) {
            super(cause.getMessage(), cause);
            this.objectId = objectId;
        }

        private String objectId() {
            return objectId;
        }
    }
}
