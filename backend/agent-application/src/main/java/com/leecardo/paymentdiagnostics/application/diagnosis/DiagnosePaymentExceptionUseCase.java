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

/**
 * 诊断支付异常用例，编排订单、支付、消息、补偿与 Trace 五类事实收集并委托规则引擎诊断。
 */
public final class DiagnosePaymentExceptionUseCase {

    private final OrderQueryPort orderQueryPort;
    private final PaymentQueryPort paymentQueryPort;
    private final MessageQueryPort messageQueryPort;
    private final CompensationQueryPort compensationQueryPort;
    private final TraceQueryPort traceQueryPort;
    private final DeterministicDiagnosisRules rules;
    private final Clock clock;
    private final DataMode dataMode;

    /**
     * 创建诊断用例，注入所有事实查询端口、确定性规则引擎、时钟和数据模式。
     *
     * @param orderQueryPort 订单查询端口
     * @param paymentQueryPort 支付流水查询端口
     * @param messageQueryPort 消息投递查询端口
     * @param compensationQueryPort 补偿任务查询端口
     * @param traceQueryPort 调用链路摘要查询端口
     * @param rules 确定性诊断规则引擎
     * @param clock 统一观察时间来源
     * @param dataMode 当前事实数据模式
     */
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

    /**
     * 按固定顺序收集 order -> payment -> message -> compensation -> trace 五类事实并执行诊断。
     *
     * <p>订单缺失是业务记录不存在，会转换为 OrderNotFoundException；任一下游查询失败不得被当作业务记录缺失，
     * 应由端口以异常表达。observedAt 只从 Clock 获取一次，保证规则引擎对所有事实使用同一观察时刻。
     *
     * @param orderId 外部传入的订单号
     * @return 支付异常诊断结果
     * @throws OrderNotFoundException 当订单事实确认不存在时抛出
     */
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


    /**
     * 校验端口返回值不为 null，避免把适配器契约错误误判为缺失事实。
     */
    private static <T> T requireNonNull(T result, String portName) {
        return Objects.requireNonNull(result, portName + " query must not return null");
    }
}
