package com.leecardo.paymentdiagnostics.infrastructure.simulation;

import java.time.Clock;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leecardo.paymentdiagnostics.application.port.CompensationQueryPort;
import com.leecardo.paymentdiagnostics.application.port.MessageQueryPort;
import com.leecardo.paymentdiagnostics.application.port.OrderQueryPort;
import com.leecardo.paymentdiagnostics.application.port.PaymentQueryPort;
import com.leecardo.paymentdiagnostics.application.port.TraceQueryPort;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;

/**
 * 仿真场景的基础设施配置类。
 * <p>
 * 仅在 {@code simulation} profile 激活时生效，负责从场景资源创建
 * {@link SimulationFactStore}，并把该事实存储适配成应用层需要的五类查询端口。
 */
@Configuration(proxyBeanMethods = false)
@Profile("simulation")
public class SimulationConfiguration {

    /**
     * 读取 {@code app.simulation.scenarios} 指定的 JSON 场景资源，构建共享的仿真事实存储。
     */
    @Bean
    SimulationFactStore simulationFactStore(
            ObjectMapper mapper,
            @Value("${app.simulation.scenarios:classpath:simulation/payment-diagnosis-scenarios.json}") Resource resource) {
        SimulationScenarioDocument document = new SimulationScenarioLoader(mapper).load(resource);
        return new SimulationFactStore(document);
    }

    /**
     * 暴露支付事实查询端口，委托给同一个仿真事实存储实例。
     */
    @Bean
    PaymentQueryPort simulationPaymentQueryPort(@Qualifier("simulationFactStore") SimulationFactStore store) {
        return store.paymentQueryPort();
    }

    /**
     * 暴露消息投递事实查询端口，委托给同一个仿真事实存储实例。
     */
    @Bean
    MessageQueryPort simulationMessageQueryPort(@Qualifier("simulationFactStore") SimulationFactStore store) {
        return store.messageQueryPort();
    }

    /**
     * 暴露补偿任务事实查询端口，委托给同一个仿真事实存储实例。
     */
    @Bean
    CompensationQueryPort simulationCompensationQueryPort(@Qualifier("simulationFactStore") SimulationFactStore store) {
        return store.compensationQueryPort();
    }

    /**
     * 暴露链路摘要事实查询端口，委托给同一个仿真事实存储实例。
     */
    @Bean
    TraceQueryPort simulationTraceQueryPort(@Qualifier("simulationFactStore") SimulationFactStore store) {
        return store.traceQueryPort();
    }

    /**
     * 注册固定时钟 Bean，使用场景文档的观测时间作为仿真环境的当前时间。
     */
    @Bean("simulationClock")
    Clock simulationClock(@Qualifier("simulationFactStore") SimulationFactStore store) {
        return Clock.fixed(store.observedAt(), ZoneOffset.UTC);
    }
}
