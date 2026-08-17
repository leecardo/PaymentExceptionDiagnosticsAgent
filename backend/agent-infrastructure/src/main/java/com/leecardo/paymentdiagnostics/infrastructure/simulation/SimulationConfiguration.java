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

@Configuration(proxyBeanMethods = false)
@Profile("simulation")
public class SimulationConfiguration {

    @Bean
    SimulationFactStore simulationFactStore(
            ObjectMapper mapper,
            @Value("${app.simulation.scenarios:classpath:simulation/payment-diagnosis-scenarios.json}") Resource resource) {
        SimulationScenarioDocument document = new SimulationScenarioLoader(mapper).load(resource);
        return new SimulationFactStore(document);
    }

    @Bean
    PaymentQueryPort simulationPaymentQueryPort(@Qualifier("simulationFactStore") SimulationFactStore store) {
        return store.paymentQueryPort();
    }

    @Bean
    MessageQueryPort simulationMessageQueryPort(@Qualifier("simulationFactStore") SimulationFactStore store) {
        return store.messageQueryPort();
    }

    @Bean
    CompensationQueryPort simulationCompensationQueryPort(@Qualifier("simulationFactStore") SimulationFactStore store) {
        return store.compensationQueryPort();
    }

    @Bean
    TraceQueryPort simulationTraceQueryPort(@Qualifier("simulationFactStore") SimulationFactStore store) {
        return store.traceQueryPort();
    }

    @Bean("simulationClock")
    Clock simulationClock(@Qualifier("simulationFactStore") SimulationFactStore store) {
        return Clock.fixed(store.observedAt(), ZoneOffset.UTC);
    }
}
