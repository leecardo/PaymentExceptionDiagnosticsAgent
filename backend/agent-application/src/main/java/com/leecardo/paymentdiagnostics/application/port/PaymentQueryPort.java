package com.leecardo.paymentdiagnostics.application.port;

import java.util.List;

import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.PaymentTransaction;

public interface PaymentQueryPort {

    List<PaymentTransaction> findByOrderId(OrderId orderId);
}
