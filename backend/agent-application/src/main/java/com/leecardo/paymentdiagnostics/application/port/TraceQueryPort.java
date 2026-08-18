package com.leecardo.paymentdiagnostics.application.port;

import java.util.Optional;

import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.TraceSummary;

/**
 * 调用链路摘要查询端口，为应用层诊断提供跨系统 Trace 完整性与时间窗口事实。
 */
public interface TraceQueryPort {

    /**
     * 查询指定订单的调用链路摘要；缺少 Trace 时返回空 Optional，由规则引擎判定链路缺口。
     *
     * @param orderId 订单标识
     * @return 调用链路摘要；不存在时为空
     */
    Optional<TraceSummary> findByOrderId(OrderId orderId);
}
