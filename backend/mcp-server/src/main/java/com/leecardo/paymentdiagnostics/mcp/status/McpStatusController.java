package com.leecardo.paymentdiagnostics.mcp.status;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
public class McpStatusController {

    @GetMapping
    McpStatus status() {
        return new McpStatus("payment-diagnostics-mcp-server", "UP", "/mcp");
    }

    record McpStatus(String service, String state, String endpoint) {
    }
}
