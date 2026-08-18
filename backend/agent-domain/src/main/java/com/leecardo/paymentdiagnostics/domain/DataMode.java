package com.leecardo.paymentdiagnostics.domain;

/**
 * 诊断数据模式词表，描述诊断数据来自内置模拟数据还是真实 PostgreSQL 数据源。
 */
public enum DataMode {
    /** 使用确定性模拟数据，适合本地演示和稳定测试。 */
    SIMULATION,
    /** 使用 PostgreSQL 生产形态数据源，适合接入真实订单、支付和消息事实。 */
    POSTGRES
}
