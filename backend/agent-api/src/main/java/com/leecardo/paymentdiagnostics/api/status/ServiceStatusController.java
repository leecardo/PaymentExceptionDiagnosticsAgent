package com.leecardo.paymentdiagnostics.api.status;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务状态接口控制器。
 *
 * <p>{@code GET /api/status} 返回固定形态 {@code {service, state}}，用于探活和基础可用性检查。</p>
 */
@RestController
@RequestMapping("/api/status")
public class ServiceStatusController {

    /**
     * 返回当前 API 服务名称和运行状态。
     *
     * @return 包含 {@code service} 与 {@code state} 字段的状态响应
     */
    @GetMapping
    ServiceStatus status() {
        return new ServiceStatus("payment-diagnostics-agent-api", "UP");
    }

    /**
     * 状态接口响应体。
     *
     * @param service 服务标识
     * @param state 服务状态
     */
    record ServiceStatus(String service, String state) {
    }
}
