package com.leecardo.paymentdiagnostics.api.config;

import com.leecardo.paymentdiagnostics.application.diagnosis.DiagnosePaymentExceptionUseCase;
import com.leecardo.paymentdiagnostics.application.order.GetOrderUseCase;
import com.leecardo.paymentdiagnostics.api.diagnosis.DiagnosisController;
import com.leecardo.paymentdiagnostics.api.order.OrderController;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Verifies that without the simulation profile, no diagnostic beans
 * or controllers exist. This prevents accidental exposure of simulation
 * results as production facts.
 */
@SpringBootTest
class SimulationProfileIsolationTest {

    @Test
    void noDiagnosticBeansWithoutSimulationProfile(ApplicationContext context) {
        assertFalse(
                context.containsBean("getOrderUseCase"),
                "GetOrderUseCase must not exist without simulation profile");
        assertFalse(
                context.containsBean("diagnosePaymentExceptionUseCase"),
                "DiagnosePaymentExceptionUseCase must not exist without simulation profile");
    }

    @Test
    void noDiagnosticControllersWithoutSimulationProfile(ApplicationContext context) {
        String[] controllerBeans = context.getBeanNamesForType(OrderController.class);
        assertFalse(controllerBeans.length > 0, "OrderController must not exist without simulation profile");

        String[] diagnosisControllerBeans = context.getBeanNamesForType(DiagnosisController.class);
        assertFalse(diagnosisControllerBeans.length > 0, "DiagnosisController must not exist without simulation profile");
    }
}
