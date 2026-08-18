package com.leecardo.paymentdiagnostics.infrastructure.simulation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.leecardo.paymentdiagnostics.application.port.CompensationQueryPort;
import com.leecardo.paymentdiagnostics.application.port.FactQueryException;
import com.leecardo.paymentdiagnostics.application.port.MessageQueryPort;
import com.leecardo.paymentdiagnostics.application.port.OrderQueryPort;
import com.leecardo.paymentdiagnostics.application.port.PaymentQueryPort;
import com.leecardo.paymentdiagnostics.application.port.TraceQueryPort;
import com.leecardo.paymentdiagnostics.domain.CompensationTask;
import com.leecardo.paymentdiagnostics.domain.MessageDelivery;
import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;
import com.leecardo.paymentdiagnostics.domain.PaymentTransaction;
import com.leecardo.paymentdiagnostics.domain.TraceSummary;

/**
 * 仿真事实存储，作为五类查询端口共享的不可变内存适配器。
 * <p>
 * 构造时把场景文档中的订单、支付、消息、补偿和链路事实索引为不可变 Map；
 * 查询时先检查按事实源与订单号配置的故障，命中后抛出 {@link FactQueryException}。
 */
public final class SimulationFactStore implements OrderQueryPort {

    private static final List<?> EMPTY_LIST = List.of();

    private final Map<OrderId, OrderSnapshot> ordersById;
    private final Map<OrderId, List<PaymentTransaction>> paymentsByOrderId;
    private final Map<OrderId, List<MessageDelivery>> messagesByOrderId;
    private final Map<OrderId, List<CompensationTask>> compensationsByOrderId;
    private final Map<OrderId, TraceSummary> tracesByOrderId;
    private final Instant observedAt;
    private final PaymentQueryPort paymentQueryPort;
    private final MessageQueryPort messageQueryPort;
    private final CompensationQueryPort compensationQueryPort;
    private final TraceQueryPort traceQueryPort;
    private final Map<SimulationFactSource, Map<OrderId, FactQueryException.Kind>> failuresBySourceAndOrderId;

    /**
     * 从场景文档建立五类事实索引，并创建端口访问器使用的方法引用或内部 lambda。
     * <p>
     * 订单按 {@link OrderId} 建立 {@link LinkedHashMap}，用于在构建阶段保留场景中的插入顺序；
     * 支付、消息和补偿按 {@code orderId} 分组；所有最终 Map 均通过 {@link Map#copyOf(Map)} 固化。
     */
    public SimulationFactStore(SimulationScenarioDocument document) {
        Objects.requireNonNull(document, "document must not be null");
        observedAt = document.observedAt();
        ordersById = ordersById(document.orders());
        Set<OrderId> knownOrderIds = ordersById.keySet();
        paymentsByOrderId = groupedByOrderId(document.paymentTransactions(), knownOrderIds, PaymentTransaction::transactionId, PaymentTransaction::orderId, "transactionId", "payment");
        messagesByOrderId = groupedByOrderId(document.messageDeliveries(), knownOrderIds, MessageDelivery::deliveryId, MessageDelivery::orderId, "deliveryId", "message");
        compensationsByOrderId = groupedByOrderId(document.compensationTasks(), knownOrderIds, CompensationTask::taskId, CompensationTask::orderId, "taskId", "compensation");
        tracesByOrderId = tracesByOrderId(document.traceSummaries(), knownOrderIds);
        failuresBySourceAndOrderId = failuresBySourceAndOrderId(document.failures(), knownOrderIds);
        paymentQueryPort = this::findPaymentsByOrderId;
        messageQueryPort = this::findMessagesByOrderId;
        compensationQueryPort = this::findCompensationsByOrderId;
        traceQueryPort = this::findTraceByOrderId;
    }

    /**
     * 返回场景声明的观测时间，用于仿真固定时钟。
     */
    public Instant observedAt() {
        return observedAt;
    }

    /**
     * 返回订单查询端口；当前存储自身实现 {@link OrderQueryPort}。
     */
    public OrderQueryPort orderQueryPort() {
        return this;
    }

    /**
     * 返回支付查询端口，内部为指向当前存储查询方法的方法引用。
     */
    public PaymentQueryPort paymentQueryPort() {
        return paymentQueryPort;
    }

    /**
     * 返回消息查询端口，内部为指向当前存储查询方法的方法引用。
     */
    public MessageQueryPort messageQueryPort() {
        return messageQueryPort;
    }

    /**
     * 返回补偿查询端口，内部为指向当前存储查询方法的方法引用。
     */
    public CompensationQueryPort compensationQueryPort() {
        return compensationQueryPort;
    }

    /**
     * 返回链路查询端口，内部为指向当前存储查询方法的方法引用。
     */
    public TraceQueryPort traceQueryPort() {
        return traceQueryPort;
    }

    /**
     * 按订单号查询订单快照，查询前先执行 ORDER 源故障检查。
     */
    @Override
    public Optional<OrderSnapshot> findById(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        throwIfConfiguredFailure(SimulationFactSource.ORDER, orderId);
        return Optional.ofNullable(ordersById.get(orderId));
    }

    /**
     * 按订单号查询支付交易；无匹配事实时返回 {@link List#of()} 而不是 {@code null}。
     */
    public List<PaymentTransaction> findPaymentsByOrderId(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        throwIfConfiguredFailure(SimulationFactSource.PAYMENT, orderId);
        return listOrEmpty(paymentsByOrderId.get(orderId));
    }

    /**
     * 按订单号查询消息投递；无匹配事实时返回 {@link List#of()} 而不是 {@code null}。
     */
    public List<MessageDelivery> findMessagesByOrderId(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        throwIfConfiguredFailure(SimulationFactSource.MESSAGE, orderId);
        return listOrEmpty(messagesByOrderId.get(orderId));
    }

    /**
     * 按订单号查询补偿任务；无匹配事实时返回 {@link List#of()} 而不是 {@code null}。
     */
    public List<CompensationTask> findCompensationsByOrderId(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        throwIfConfiguredFailure(SimulationFactSource.COMPENSATION, orderId);
        return listOrEmpty(compensationsByOrderId.get(orderId));
    }

    /**
     * 按订单号查询链路摘要，查询前先执行 TRACE 源故障检查。
     */
    public Optional<TraceSummary> findTraceByOrderId(OrderId orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        throwIfConfiguredFailure(SimulationFactSource.TRACE, orderId);
        return Optional.ofNullable(tracesByOrderId.get(orderId));
    }

    /**
     * 将缺失的分组查询结果统一转换为空不可变列表，保证成功查询不返回 {@code null}。
     */
    @SuppressWarnings("unchecked")
    private static <T> List<T> listOrEmpty(List<T> values) {
        if (values == null) {
            return (List<T>) EMPTY_LIST;
        }
        return values;
    }

    /**
     * 按事实源与订单号查找故障配置；命中时在读取事实前抛出端口异常。
     */
    private void throwIfConfiguredFailure(SimulationFactSource source, OrderId orderId) {
        FactQueryException.Kind kind = failuresBySourceAndOrderId.getOrDefault(source, Map.of()).get(orderId);
        if (kind != null) {
            throw new FactQueryException(kind, source + " facts for orderId " + orderId.value() + " are " + kind);
        }
    }

    /**
     * 使用 {@link LinkedHashMap} 按 {@link OrderId} 索引订单，保留构建期插入顺序并拒绝重复订单号。
     */
    private static Map<OrderId, OrderSnapshot> ordersById(List<OrderSnapshot> orders) {
        Map<OrderId, OrderSnapshot> result = new LinkedHashMap<>();
        for (OrderSnapshot order : orders) {
            OrderSnapshot previous = result.putIfAbsent(order.orderId(), order);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate orderId " + order.orderId().value());
            }
        }
        return Map.copyOf(result);
    }

    /**
     * 按订单号索引链路摘要，校验 traceId 和 orderId 唯一性后复制为不可变 Map。
     */
    private static Map<OrderId, TraceSummary> tracesByOrderId(List<TraceSummary> traces, Set<OrderId> knownOrderIds) {
        Set<String> traceIds = new HashSet<>();
        Map<OrderId, TraceSummary> result = new HashMap<>();
        for (TraceSummary trace : traces) {
            if (!traceIds.add(trace.traceId())) {
                throw new IllegalArgumentException("duplicate traceId " + trace.traceId());
            }
            requireKnownOrderId(knownOrderIds, trace.orderId(), "trace " + trace.traceId());
            TraceSummary previous = result.putIfAbsent(trace.orderId(), trace);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate trace for orderId " + trace.orderId().value());
            }
        }
        return Map.copyOf(result);
    }

    /**
     * 构建故障索引，外层以事实源为键，内层以订单号为键，最终两层 Map 都不可变。
     */
    private static Map<SimulationFactSource, Map<OrderId, FactQueryException.Kind>> failuresBySourceAndOrderId(
            List<SimulationScenarioDocument.FailureRecord> failures,
            Set<OrderId> knownOrderIds) {
        Map<SimulationFactSource, Map<OrderId, FactQueryException.Kind>> result = new EnumMap<>(SimulationFactSource.class);
        for (SimulationScenarioDocument.FailureRecord failure : failures) {
            OrderId orderId = new OrderId(failure.orderId());
            requireKnownOrderId(knownOrderIds, orderId, "failure");
            Map<OrderId, FactQueryException.Kind> sourceFailures = result.computeIfAbsent(failure.source(), ignored -> new HashMap<>());
            FactQueryException.Kind previous = sourceFailures.putIfAbsent(orderId, failure.kind());
            if (previous != null) {
                throw new IllegalArgumentException("duplicate failure for " + failure.source() + "/" + orderId.value());
            }
        }
        result.replaceAll((source, sourceFailures) -> Map.copyOf(sourceFailures));
        return Map.copyOf(result);
    }

    /**
     * 将支付、消息、补偿等多条事实按订单号分组，并将每组列表和外层 Map 固化为不可变对象。
     */
    private static <T> Map<OrderId, List<T>> groupedByOrderId(
            List<T> facts,
            Set<OrderId> knownOrderIds,
            IdExtractor<T> idExtractor,
            OrderIdExtractor<T> orderIdExtractor,
            String idName,
            String factName) {
        Set<String> ids = new HashSet<>();
        Map<OrderId, List<T>> mutable = new LinkedHashMap<>();
        for (T fact : facts) {
            String id = idExtractor.id(fact);
            if (!ids.add(id)) {
                throw new IllegalArgumentException("duplicate " + idName + " " + id);
            }
            OrderId orderId = orderIdExtractor.orderId(fact);
            requireKnownOrderId(knownOrderIds, orderId, factName + " " + id);
            mutable.computeIfAbsent(orderId, ignored -> new ArrayList<>()).add(fact);
        }
        Map<OrderId, List<T>> result = new LinkedHashMap<>();
        mutable.forEach((orderId, values) -> result.put(orderId, List.copyOf(values)));
        return Map.copyOf(result);
    }

    /**
     * 校验事实引用的订单号必须存在于订单事实集合中。
     */
    private static void requireKnownOrderId(Set<OrderId> knownOrderIds, OrderId orderId, String owner) {
        if (!knownOrderIds.contains(orderId)) {
            throw new IllegalArgumentException(owner + " references unknown orderId " + orderId.value());
        }
    }

    /**
     * 提取事实自身逻辑 ID，用于重复事实校验和错误定位。
     */
    @FunctionalInterface
    private interface IdExtractor<T> {
        String id(T value);
    }

    /**
     * 提取事实归属订单号，用于按订单分组和引用校验。
     */
    @FunctionalInterface
    private interface OrderIdExtractor<T> {
        OrderId orderId(T value);
    }
}
