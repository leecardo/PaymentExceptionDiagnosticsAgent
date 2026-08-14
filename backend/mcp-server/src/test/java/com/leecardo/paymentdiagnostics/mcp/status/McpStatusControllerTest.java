package com.leecardo.paymentdiagnostics.mcp.status;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(McpStatusController.class)
class McpStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void reportsMcpServiceStatus() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("payment-diagnostics-mcp-server"))
                .andExpect(jsonPath("$.state").value("UP"))
                .andExpect(jsonPath("$.endpoint").value("/mcp"));
    }
}
