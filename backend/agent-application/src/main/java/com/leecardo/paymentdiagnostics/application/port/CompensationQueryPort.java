package com.leecardo.paymentdiagnostics.application.port;

import java.util.List;

import com.leecardo.paymentdiagnostics.domain.CompensationTask;
import com.leecardo.paymentdiagnostics.domain.OrderId;

/**
 * 补偿任务查询端口，为诊断流程提供订单相关补偿创建、重试与失败事实。
 */
public interface CompensationQueryPort {

    /**
     * 查询指定订单关联的补偿任务；空列表表示未发现补偿任务，不表示补偿查询端异常。
     *
     * @param orderId 订单标识
     * @return 与订单关联的补偿任务列表
     */
    List<CompensationTask> findByOrderId(OrderId orderId);
}
