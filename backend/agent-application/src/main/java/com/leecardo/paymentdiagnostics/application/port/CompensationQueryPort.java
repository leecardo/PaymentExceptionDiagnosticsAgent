package com.leecardo.paymentdiagnostics.application.port;

import java.util.List;

import com.leecardo.paymentdiagnostics.domain.CompensationTask;
import com.leecardo.paymentdiagnostics.domain.OrderId;

public interface CompensationQueryPort {

    List<CompensationTask> findByOrderId(OrderId orderId);
}
