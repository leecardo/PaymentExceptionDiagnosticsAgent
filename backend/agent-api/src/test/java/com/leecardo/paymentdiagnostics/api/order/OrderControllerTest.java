package com.leecardo.paymentdiagnostics.api.order;

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
 * Contract tests for OrderController using real simulation data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("simulation")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsSafeOrderJson() throws Exception {
        mockMvc.perform(get("/api/orders/SIM-NORMAL-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("SIM-NORMAL-001"))
                .andExpect(jsonPath("$.role").value("SINGLE"))
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.productName").value("Synthetic normal package"))
                .andExpect(jsonPath("$.goodsCount").value(1))
                .andExpect(jsonPath("$.unitPrice").value(42.00))
                .andExpect(jsonPath("$.orderAmount").value(42.00))
                .andExpect(jsonPath("$.paymentAmount").value(42.00))
                .andExpect(jsonPath("$.orderedAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void invalidOrderIdReturns400() throws Exception {
        mockMvc.perform(get("/api/orders/bad@id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_ID"))
                .andExpect(jsonPath("$.message").value("orderId is invalid"));
    }

    @Test
    void orderNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/orders/SIM-MISSING-001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("order was not found"));
    }

    @Test
    void responseHasNoSensitiveFields() throws Exception {
        mockMvc.perform(get("/api/orders/SIM-NORMAL-001"))
                .andExpect(jsonPath("$.customerName").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.secret").doesNotExist());
    }

    @Test
    void existingStatusEndpointStillWorks() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("payment-diagnostics-agent-api"))
                .andExpect(jsonPath("$.state").value("UP"));
    }
}
