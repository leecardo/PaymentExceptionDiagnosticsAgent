package com.leecardo.paymentdiagnostics.application.order;

import java.util.Objects;

import com.leecardo.paymentdiagnostics.application.port.OrderQueryPort;
import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;

public final class GetOrderUseCase {

    private final OrderQueryPort orderQueryPort;

    public GetOrderUseCase(OrderQueryPort orderQueryPort) {
        this.orderQueryPort = Objects.requireNonNull(orderQueryPort, "orderQueryPort must not be null");
    }

    public OrderSnapshot get(String orderId) {
        OrderId parsedOrderId = new OrderId(orderId);
        return orderQueryPort.findById(parsedOrderId)
                .orElseThrow(() -> new OrderNotFoundException(parsedOrderId));
    }
}
