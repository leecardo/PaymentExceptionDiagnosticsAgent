package com.leecardo.paymentdiagnostics.application.port;

import java.util.List;

import com.leecardo.paymentdiagnostics.domain.MessageDelivery;
import com.leecardo.paymentdiagnostics.domain.OrderId;

/**
 * 消息投递查询端口，向应用层暴露订单相关的异步消息发送与消费事实。
 */
public interface MessageQueryPort {

    /**
     * 查询指定订单关联的消息投递记录；空列表表示未发现投递事实，不表示消息查询端异常。
     *
     * @param orderId 订单标识
     * @return 与订单关联的消息投递列表
     */
    List<MessageDelivery> findByOrderId(OrderId orderId);
}
