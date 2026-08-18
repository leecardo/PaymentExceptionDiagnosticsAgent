package com.leecardo.paymentdiagnostics.application.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import com.leecardo.paymentdiagnostics.application.port.FactQueryException;
import com.leecardo.paymentdiagnostics.application.port.OrderQueryPort;
import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.OrderRole;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;
import com.leecardo.paymentdiagnostics.domain.OrderStatus;

import org.junit.jupiter.api.Test;

class GetOrderUseCaseTest {

    @Test
    void returnsFoundOrder() {
        OrderSnapshot order = singleOrder("order-123");
        RecordingOrderQueryPort port = new RecordingOrderQueryPort(Optional.of(order));
        GetOrderUseCase useCase = new GetOrderUseCase(port);

        OrderSnapshot result = useCase.get(" order-123 ");

        assertSame(order, result);
        assertEquals(new OrderId("order-123"), port.queriedOrderId);
        assertEquals(1, port.queryCount);
    }

    @Test
    void throwsOrderNotFoundRetainingOrderIdWhenSuccessfulQueryIsEmpty() {
        RecordingOrderQueryPort port = new RecordingOrderQueryPort(Optional.empty());
        GetOrderUseCase useCase = new GetOrderUseCase(port);

        OrderNotFoundException exception = assertThrows(OrderNotFoundException.class, () -> useCase.get("missing-001"));

        assertEquals(new OrderId("missing-001"), exception.orderId());
        assertEquals("Order not found: missing-001", exception.getMessage());
        assertEquals(new OrderId("missing-001"), port.queriedOrderId);
        assertEquals(1, port.queryCount);
    }

    @Test
    void rejectsBlankOrderIdBeforeQueryingPort() {
        RecordingOrderQueryPort port = new RecordingOrderQueryPort(Optional.of(singleOrder("unused")));
        GetOrderUseCase useCase = new GetOrderUseCase(port);

        assertThrows(IllegalArgumentException.class, () -> useCase.get(" \t\n"));

        assertEquals(0, port.queryCount);
    }

    @Test
    void rejectsIllegalOrderIdBeforeQueryingPort() {
        RecordingOrderQueryPort port = new RecordingOrderQueryPort(Optional.of(singleOrder("unused")));
        GetOrderUseCase useCase = new GetOrderUseCase(port);

        assertThrows(IllegalArgumentException.class, () -> useCase.get("order/123"));

        assertEquals(0, port.queryCount);
    }

    @Test
    void propagatesUnavailableFactQueryExceptionUnchanged() {
        FactQueryException failure = new FactQueryException(FactQueryException.Kind.UNAVAILABLE, "order facts unavailable");
        RecordingOrderQueryPort port = new RecordingOrderQueryPort(failure);
        GetOrderUseCase useCase = new GetOrderUseCase(port);

        FactQueryException thrown = assertThrows(FactQueryException.class, () -> useCase.get("order-123"));

        assertSame(failure, thrown);
        assertEquals(FactQueryException.Kind.UNAVAILABLE, thrown.kind());
        assertEquals(1, port.queryCount);
    }

    @Test
    void propagatesTimeoutFactQueryExceptionUnchanged() {
        FactQueryException failure = new FactQueryException(FactQueryException.Kind.TIMEOUT, "order facts timed out");
        RecordingOrderQueryPort port = new RecordingOrderQueryPort(failure);
        GetOrderUseCase useCase = new GetOrderUseCase(port);

        FactQueryException thrown = assertThrows(FactQueryException.class, () -> useCase.get("order-123"));

        assertSame(failure, thrown);
        assertEquals(FactQueryException.Kind.TIMEOUT, thrown.kind());
        assertEquals(1, port.queryCount);
    }

    @Test
    void requiresFactQueryExceptionKindAndMessage() {
        assertThrows(NullPointerException.class, () -> new FactQueryException(null, "order facts unavailable"));
        assertThrows(NullPointerException.class, () -> new FactQueryException(FactQueryException.Kind.UNAVAILABLE, null));
        assertThrows(IllegalArgumentException.class, () -> new FactQueryException(FactQueryException.Kind.UNAVAILABLE, " \t\n"));
    }

    @Test
    void requiresOrderNotFoundOrderId() {
        assertThrows(NullPointerException.class, () -> new OrderNotFoundException(null));
    }

    @Test
    void requiresOrderQueryPort() {
        assertThrows(NullPointerException.class, () -> new GetOrderUseCase(null));
    }

    private static OrderSnapshot singleOrder(String orderId) {
        Instant orderedAt = Instant.parse("2026-08-17T10:15:30Z");
        return new OrderSnapshot(
                new OrderId(orderId),
                null,
                OrderRole.SINGLE,
                "product-001",
                "Diagnostic Product",
                "course",
                1,
                new BigDecimal("99.00"),
                new BigDecimal("99.00"),
                new BigDecimal("99.00"),
                "WECHAT",
                "provider-order-001",
                "WEB",
                OrderStatus.PAID,
                orderedAt,
                orderedAt.plusSeconds(60),
                orderedAt.minusSeconds(30),
                orderedAt.plusSeconds(60));
    }

    private static final class RecordingOrderQueryPort implements OrderQueryPort {
        private final Optional<OrderSnapshot> result;
        private final FactQueryException failure;
        private OrderId queriedOrderId;
        private int queryCount;

        private RecordingOrderQueryPort(Optional<OrderSnapshot> result) {
            this.result = result;
            this.failure = null;
        }

        private RecordingOrderQueryPort(FactQueryException failure) {
            this.result = Optional.empty();
            this.failure = failure;
        }

        @Override
        public Optional<OrderSnapshot> findById(OrderId orderId) {
            queryCount++;
            queriedOrderId = orderId;
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }
}
