package com.leecardo.paymentdiagnostics.application.port;

import java.util.Optional;

import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;

/**
 * 订单查询端口，作为应用层到订单事实来源的六边形架构出站端口。
 */
public interface OrderQueryPort {

    /**
     * 按订单标识查询订单快照；未找到业务记录时返回空 Optional，不表示查询端不可用。
     *
     * @param orderId 订单标识
     * @return 订单快照；不存在时为空
     */
    Optional<OrderSnapshot> findById(OrderId orderId);
}
