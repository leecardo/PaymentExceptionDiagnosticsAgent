package com.leecardo.paymentdiagnostics.application.port;

import java.util.Optional;

import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.TraceSummary;

public interface TraceQueryPort {

    Optional<TraceSummary> findByOrderId(OrderId orderId);
}
