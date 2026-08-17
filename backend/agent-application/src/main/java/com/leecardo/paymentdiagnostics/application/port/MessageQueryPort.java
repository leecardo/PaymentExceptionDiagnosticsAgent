package com.leecardo.paymentdiagnostics.application.port;

import java.util.List;

import com.leecardo.paymentdiagnostics.domain.MessageDelivery;
import com.leecardo.paymentdiagnostics.domain.OrderId;

public interface MessageQueryPort {

    List<MessageDelivery> findByOrderId(OrderId orderId);
}
