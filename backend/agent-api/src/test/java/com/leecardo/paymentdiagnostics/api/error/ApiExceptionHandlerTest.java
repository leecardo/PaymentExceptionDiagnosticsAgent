package com.leecardo.paymentdiagnostics.api.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.leecardo.paymentdiagnostics.application.order.OrderNotFoundException;
import com.leecardo.paymentdiagnostics.application.port.FactQueryException;
import com.leecardo.paymentdiagnostics.domain.OrderId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Verifies stable error response shapes for all mapped exception types.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("simulation")
@Import(ApiExceptionHandlerTest.TestControllerConfig.class)
class ApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invalidOrderIdReturns400() throws Exception {
        mockMvc.perform(get("/test/error").param("type", "INVALID_INPUT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_ID"))
                .andExpect(jsonPath("$.message").value("orderId is invalid"));
    }

    @Test
    void orderNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/test/error").param("type", "ORDER_NOT_FOUND"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("order was not found"));
    }

    @Test
    void factSourceUnavailableReturns503() throws Exception {
        mockMvc.perform(get("/test/error").param("type", "FACT_UNAVAILABLE"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FACT_SOURCE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("diagnostic fact source is unavailable"));
    }

    @Test
    void factSourceTimeoutReturns503() throws Exception {
        mockMvc.perform(get("/test/error").param("type", "FACT_TIMEOUT"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FACT_SOURCE_TIMEOUT"))
                .andExpect(jsonPath("$.message").value("diagnostic fact source timed out"));
    }

    @Test
    void errorResponseDoesNotExposeStackTrace() throws Exception {
        mockMvc.perform(get("/test/error").param("type", "ORDER_NOT_FOUND"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.sql").doesNotExist())
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }

    @TestConfiguration
    static class TestControllerConfig {
        @Bean
        ErrorTestController errorTestController() {
            return new ErrorTestController();
        }
    }

    @RestController
    static class ErrorTestController {
        @GetMapping("/test/error")
        void triggerError(@RequestParam String type) {
            switch (type) {
                case "INVALID_INPUT" -> throw new IllegalArgumentException("bad id");
                case "ORDER_NOT_FOUND" ->
                        throw new OrderNotFoundException(new OrderId("SIM-NOT-FOUND-001"));
                case "FACT_UNAVAILABLE" ->
                        throw new FactQueryException(FactQueryException.Kind.UNAVAILABLE, "upstream down");
                case "FACT_TIMEOUT" ->
                        throw new FactQueryException(FactQueryException.Kind.TIMEOUT, "timed out");
                default -> throw new IllegalStateException("unknown type: " + type);
            }
        }
    }
}
