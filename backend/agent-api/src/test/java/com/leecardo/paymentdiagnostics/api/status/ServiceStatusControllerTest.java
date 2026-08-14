package com.leecardo.paymentdiagnostics.api.status;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ServiceStatusController.class)
class ServiceStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsApiServiceStatus() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("payment-diagnostics-agent-api"))
                .andExpect(jsonPath("$.state").value("UP"));
    }
}
