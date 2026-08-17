package com.leecardo.paymentdiagnostics.application.port;

import java.util.Optional;

import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;

public interface OrderQueryPort {

    Optional<OrderSnapshot> findById(OrderId orderId);
}
