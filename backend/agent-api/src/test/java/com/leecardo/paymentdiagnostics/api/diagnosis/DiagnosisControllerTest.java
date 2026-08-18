package com.leecardo.paymentdiagnostics.api.diagnosis;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contract tests for DiagnosisController using real simulation data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("simulation")
class DiagnosisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsDiagnosisWithSimulationMode() throws Exception {
        mockMvc.perform(get("/api/diagnoses/orders/SIM-CALLBACK-MISSING-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("SIM-CALLBACK-MISSING-001"))
                .andExpect(jsonPath("$.dataMode").value("SIMULATION"))
                .andExpect(jsonPath("$.stage").value("PAYMENT_CALLBACK"))
                .andExpect(jsonPath("$.ruleId").value("PROVIDER_SUCCEEDED_CALLBACK_MISSING"))
                .andExpect(jsonPath("$.summary").exists())
                .andExpect(jsonPath("$.evidence[0].id").exists())
                .andExpect(jsonPath("$.evidence[0].source").exists())
                .andExpect(jsonPath("$.evidence[0].summary").exists())
                .andExpect(jsonPath("$.evidence[0].observedAt").exists());
    }

    @Test
    void normalFlowReturnsNoKnownException() throws Exception {
        mockMvc.perform(get("/api/diagnoses/orders/SIM-NORMAL-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("SIM-NORMAL-001"))
                .andExpect(jsonPath("$.dataMode").value("SIMULATION"))
                .andExpect(jsonPath("$.ruleId").value("NO_KNOWN_EXCEPTION"))
                .andExpect(jsonPath("$.stage").value("COMPLETED"));
    }

    @Test
    void paymentNotStartedReturnsCorrectRule() throws Exception {
        mockMvc.perform(get("/api/diagnoses/orders/SIM-PAY-NOT-STARTED-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId").value("PAYMENT_NOT_STARTED"))
                .andExpect(jsonPath("$.dataMode").value("SIMULATION"));
    }

    @Test
    void providerFailedReturnsCorrectRule() throws Exception {
        mockMvc.perform(get("/api/diagnoses/orders/SIM-PROVIDER-FAILED-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId").value("PAYMENT_FAILED_WITH_PROVIDER_ERROR"));
    }

    @Test
    void compensationExhaustedReturnsCorrectRule() throws Exception {
        mockMvc.perform(get("/api/diagnoses/orders/SIM-COMP-EXHAUSTED-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleId").value("COMPENSATION_RETRIES_EXHAUSTED"));
    }

    @Test
    void invalidOrderIdReturns400() throws Exception {
        mockMvc.perform(get("/api/diagnoses/orders/bad@id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_ID"));
    }

    @Test
    void orderNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/diagnoses/orders/SIM-MISSING-001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void responseHasNoSensitiveFields() throws Exception {
        mockMvc.perform(get("/api/diagnoses/orders/SIM-NORMAL-001"))
                .andExpect(jsonPath("$.customerName").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.secret").doesNotExist());
    }
}
