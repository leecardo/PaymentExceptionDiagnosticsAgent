package com.leecardo.paymentdiagnostics.application.port;

import java.util.List;

import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.PaymentTransaction;

/**
 * 支付流水查询端口，屏蔽支付系统或存储适配器差异，为诊断用例提供订单维度支付事实。
 */
public interface PaymentQueryPort {

    /**
     * 查询指定订单关联的支付流水集合；空列表表示当前没有支付事实，不表示查询失败。
     *
     * @param orderId 订单标识
     * @return 与订单关联的支付流水列表
     */
    List<PaymentTransaction> findByOrderId(OrderId orderId);
}
