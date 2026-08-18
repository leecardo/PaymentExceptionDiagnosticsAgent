package com.leecardo.paymentdiagnostics.infrastructure.simulation;

/**
 * 仿真事实源枚举，用于标识五类端口事实及其故障注入来源。
 */
public enum SimulationFactSource {
    ORDER,
    PAYMENT,
    MESSAGE,
    COMPENSATION,
    TRACE
}
