package com.leecardo.paymentdiagnostics.api.config;

import java.time.Clock;

import com.leecardo.paymentdiagnostics.application.diagnosis.DiagnosePaymentExceptionUseCase;
import com.leecardo.paymentdiagnostics.application.diagnosis.DeterministicDiagnosisRules;
import com.leecardo.paymentdiagnostics.application.diagnosis.DiagnosisPolicy;
import com.leecardo.paymentdiagnostics.application.order.GetOrderUseCase;
import com.leecardo.paymentdiagnostics.application.port.CompensationQueryPort;
import com.leecardo.paymentdiagnostics.application.port.MessageQueryPort;
import com.leecardo.paymentdiagnostics.application.port.OrderQueryPort;
import com.leecardo.paymentdiagnostics.application.port.PaymentQueryPort;
import com.leecardo.paymentdiagnostics.application.port.TraceQueryPort;
import com.leecardo.paymentdiagnostics.domain.DataMode;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires diagnostic use cases under the simulation profile.
 * Controllers delegate to these beans; no controller inspects active profiles.
 *
 * <p>{@code simulation} Profile 下的诊断用例装配类。负责创建诊断策略、确定性规则、订单查询用例和支付异常诊断用例，
 * 控制器只依赖这些用例 Bean，不在协议层直接组装业务规则。</p>
 */
@Configuration(proxyBeanMethods = false)
@Profile("simulation")
@EnableConfigurationProperties(DiagnosisProperties.class)
public class DiagnosticUseCaseConfiguration {

    /**
     * 根据配置属性创建诊断策略，注入支付处理中和消息消费的超时阈值。
     *
     * @param properties 诊断阈值配置
     * @return 诊断策略
     */
    @Bean
    DiagnosisPolicy diagnosisPolicy(DiagnosisProperties properties) {
        return new DiagnosisPolicy(
                properties.getPaymentProcessingTimeout(),
                properties.getMessageConsumptionTimeout());
    }

    /**
     * 创建确定性诊断规则集合，供模拟诊断用例按固定规则判定异常阶段。
     *
     * @param policy 诊断策略阈值
     * @return 确定性诊断规则
     */
    @Bean
    DeterministicDiagnosisRules deterministicDiagnosisRules(DiagnosisPolicy policy) {
        return new DeterministicDiagnosisRules(policy);
    }

    /**
     * 创建订单查询用例，封装订单读取端口。
     *
     * @param orderQueryPort 订单查询端口
     * @return 订单查询用例
     */
    @Bean
    GetOrderUseCase getOrderUseCase(OrderQueryPort orderQueryPort) {
        return new GetOrderUseCase(orderQueryPort);
    }

    /**
     * 创建支付异常诊断用例，聚合订单、支付、消息、补偿和链路事实源端口。
     *
     * <p>该 Bean 仅在 {@code simulation} Profile 下装配，并固定使用 {@link DataMode#SIMULATION} 标记响应数据模式。</p>
     *
     * @param orderQueryPort 订单事实查询端口
     * @param paymentQueryPort 支付事实查询端口
     * @param messageQueryPort 消息事实查询端口
     * @param compensationQueryPort 补偿事实查询端口
     * @param traceQueryPort 链路事实查询端口
     * @param rules 确定性诊断规则
     * @return 支付异常诊断用例
     */
    @Bean
    DiagnosePaymentExceptionUseCase diagnosePaymentExceptionUseCase(
            OrderQueryPort orderQueryPort,
            PaymentQueryPort paymentQueryPort,
            MessageQueryPort messageQueryPort,
            CompensationQueryPort compensationQueryPort,
            TraceQueryPort traceQueryPort,
            DeterministicDiagnosisRules rules) {
        return new DiagnosePaymentExceptionUseCase(
                orderQueryPort,
                paymentQueryPort,
                messageQueryPort,
                compensationQueryPort,
                traceQueryPort,
                rules,
                Clock.systemUTC(),
                DataMode.SIMULATION);
    }
}
