package com.leecardo.paymentdiagnostics.infrastructure.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.leecardo.paymentdiagnostics.application.port.CompensationQueryPort;
import com.leecardo.paymentdiagnostics.application.port.MessageQueryPort;
import com.leecardo.paymentdiagnostics.application.port.OrderQueryPort;
import com.leecardo.paymentdiagnostics.application.port.PaymentQueryPort;
import com.leecardo.paymentdiagnostics.application.port.TraceQueryPort;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SimulationConfigurationTest {

    private static final Instant OBSERVED_AT = Instant.parse("2026-08-17T12:00:00Z");

    @Test
    void simulationBeansAreAbsentWithoutSimulationProfile() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(SimulationConfiguration.class);
            context.refresh();

            assertFalse(context.containsBean("simulationFactStore"));
            assertFalse(context.containsBean("simulationOrderQueryPort"));
            assertFalse(context.containsBean("simulationPaymentQueryPort"));
            assertFalse(context.containsBean("simulationMessageQueryPort"));
            assertFalse(context.containsBean("simulationCompensationQueryPort"));
            assertFalse(context.containsBean("simulationTraceQueryPort"));
            assertFalse(context.containsBean("simulationClock"));
            assertTrue(context.getBeansOfType(SimulationFactStore.class).isEmpty());
            assertTrue(context.getBeansOfType(OrderQueryPort.class).isEmpty());
            assertTrue(context.getBeansOfType(PaymentQueryPort.class).isEmpty());
            assertTrue(context.getBeansOfType(MessageQueryPort.class).isEmpty());
            assertTrue(context.getBeansOfType(CompensationQueryPort.class).isEmpty());
            assertTrue(context.getBeansOfType(TraceQueryPort.class).isEmpty());
            assertTrue(context.getBeansOfType(Clock.class).isEmpty());
        }
    }

    @Test
    void simulationProfileLoadsOneStoreStablePortAdaptersAndFixedClock() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("simulation");
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.register(SimulationConfiguration.class);
            context.refresh();

            SimulationFactStore store = context.getBean("simulationFactStore", SimulationFactStore.class);
            assertSame(store, context.getBean("simulationFactStore"));
            assertEquals(OBSERVED_AT, store.observedAt());

            assertSame(store, context.getBean(OrderQueryPort.class));
            assertSame(store.orderQueryPort(), context.getBean(OrderQueryPort.class));
            assertSame(store.paymentQueryPort(), context.getBean(PaymentQueryPort.class));
            assertSame(store.messageQueryPort(), context.getBean(MessageQueryPort.class));
            assertSame(store.compensationQueryPort(), context.getBean(CompensationQueryPort.class));
            assertSame(store.traceQueryPort(), context.getBean(TraceQueryPort.class));

            assertSame(context.getBean(PaymentQueryPort.class), context.getBean("simulationPaymentQueryPort"));
            assertSame(context.getBean(MessageQueryPort.class), context.getBean("simulationMessageQueryPort"));
            assertSame(context.getBean(CompensationQueryPort.class), context.getBean("simulationCompensationQueryPort"));
            assertSame(context.getBean(TraceQueryPort.class), context.getBean("simulationTraceQueryPort"));

            assertEquals(1, context.getBeansOfType(SimulationFactStore.class).size());
            assertEquals(1, context.getBeansOfType(OrderQueryPort.class).size());
            assertEquals(1, context.getBeansOfType(PaymentQueryPort.class).size());
            assertEquals(1, context.getBeansOfType(MessageQueryPort.class).size());
            assertEquals(1, context.getBeansOfType(CompensationQueryPort.class).size());
            assertEquals(1, context.getBeansOfType(TraceQueryPort.class).size());

            Clock clock = context.getBean("simulationClock", Clock.class);
            assertEquals(OBSERVED_AT, clock.instant());
            assertEquals(ZoneOffset.UTC, clock.getZone());
        }
    }

}
