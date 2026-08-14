package com.leecardo.paymentdiagnostics.api.status;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
public class ServiceStatusController {

    @GetMapping
    ServiceStatus status() {
        return new ServiceStatus("payment-diagnostics-agent-api", "UP");
    }

    record ServiceStatus(String service, String state) {
    }
}
