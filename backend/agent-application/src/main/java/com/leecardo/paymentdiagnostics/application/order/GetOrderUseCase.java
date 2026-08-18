package com.leecardo.paymentdiagnostics.application.order;

import java.util.Objects;

import com.leecardo.paymentdiagnostics.application.port.OrderQueryPort;
import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;

/**
 * 获取订单用例，负责解析外部订单号并通过订单查询端口读取订单快照。
 */
public final class GetOrderUseCase {

    private final OrderQueryPort orderQueryPort;

    /**
     * 创建获取订单用例并注入订单查询端口。
     *
     * @param orderQueryPort 订单查询端口
     */
    public GetOrderUseCase(OrderQueryPort orderQueryPort) {
        this.orderQueryPort = Objects.requireNonNull(orderQueryPort, "orderQueryPort must not be null");
    }

    /**
     * 将字符串订单号构造成领域 OrderId 后查询订单；不存在时抛出订单不存在异常。
     *
     * @param orderId 外部传入的订单号
     * @return 订单快照
     * @throws OrderNotFoundException 当订单查询端口返回空结果时抛出
     */
    public OrderSnapshot get(String orderId) {
        OrderId parsedOrderId = new OrderId(orderId);
        return orderQueryPort.findById(parsedOrderId)
                .orElseThrow(() -> new OrderNotFoundException(parsedOrderId));
    }
}
