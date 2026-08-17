package com.leecardo.paymentdiagnostics.application.diagnosis;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.leecardo.paymentdiagnostics.application.order.OrderNotFoundException;
import com.leecardo.paymentdiagnostics.application.port.CompensationQueryPort;
import com.leecardo.paymentdiagnostics.application.port.MessageQueryPort;
import com.leecardo.paymentdiagnostics.application.port.OrderQueryPort;
import com.leecardo.paymentdiagnostics.application.port.PaymentQueryPort;
import com.leecardo.paymentdiagnostics.application.port.TraceQueryPort;
import com.leecardo.paymentdiagnostics.domain.CompensationTask;
import com.leecardo.paymentdiagnostics.domain.DataMode;
import com.leecardo.paymentdiagnostics.domain.DiagnosisResult;
import com.leecardo.paymentdiagnostics.domain.MessageDelivery;
import com.leecardo.paymentdiagnostics.domain.OrderId;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;
import com.leecardo.paymentdiagnostics.domain.PaymentTransaction;
import com.leecardo.paymentdiagnostics.domain.TraceSummary;

public final class DiagnosePaymentExceptionUseCase {

    private final OrderQueryPort orderQueryPort;
    private final PaymentQueryPort paymentQueryPort;
    private final MessageQueryPort messageQueryPort;
    private final CompensationQueryPort compensationQueryPort;
    private final TraceQueryPort traceQueryPort;
    private final DeterministicDiagnosisRules rules;
    private final Clock clock;
    private final DataMode dataMode;

    public DiagnosePaymentExceptionUseCase(
            OrderQueryPort orderQueryPort,
            PaymentQueryPort paymentQueryPort,
            MessageQueryPort messageQueryPort,
            CompensationQueryPort compensationQueryPort,
            TraceQueryPort traceQueryPort,
            DeterministicDiagnosisRules rules,
            Clock clock,
            DataMode dataMode) {
        this.orderQueryPort = Objects.requireNonNull(orderQueryPort, "orderQueryPort must not be null");
        this.paymentQueryPort = Objects.requireNonNull(paymentQueryPort, "paymentQueryPort must not be null");
        this.messageQueryPort = Objects.requireNonNull(messageQueryPort, "messageQueryPort must not be null");
        this.compensationQueryPort = Objects.requireNonNull(compensationQueryPort, "compensationQueryPort must not be null");
        this.traceQueryPort = Objects.requireNonNull(traceQueryPort, "traceQueryPort must not be null");
        this.rules = Objects.requireNonNull(rules, "rules must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.dataMode = Objects.requireNonNull(dataMode, "dataMode must not be null");
    }

    public DiagnosisResult diagnose(String orderId) {
        OrderId parsedOrderId = new OrderId(orderId);
        OrderSnapshot order = requireNonNull(orderQueryPort.findById(parsedOrderId), "order")
                .orElseThrow(() -> new OrderNotFoundException(parsedOrderId));
        List<PaymentTransaction> payments = requireNonNull(paymentQueryPort.findByOrderId(parsedOrderId), "payment");
        List<MessageDelivery> messages = requireNonNull(messageQueryPort.findByOrderId(parsedOrderId), "message");
        List<CompensationTask> compensations = requireNonNull(compensationQueryPort.findByOrderId(parsedOrderId), "compensation");
        Optional<TraceSummary> trace = requireNonNull(traceQueryPort.findByOrderId(parsedOrderId), "trace");
        Instant observedAt = clock.instant();

        return rules.diagnose(new CollectedFacts(
                order,
                payments,
                messages,
                compensations,
                trace,
                observedAt,
                dataMode,
                List.of()));
    }


    private static <T> T requireNonNull(T result, String portName) {
        return Objects.requireNonNull(result, portName + " query must not return null");
    }
}
