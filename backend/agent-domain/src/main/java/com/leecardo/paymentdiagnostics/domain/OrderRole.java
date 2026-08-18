package com.leecardo.paymentdiagnostics.domain;

/**
 * 订单角色词表，用于表达诊断对象在单订单、主订单、子订单结构中的位置。
 */
public enum OrderRole {
    /** 独立订单，不属于主子订单结构。 */
    SINGLE,
    /** 主订单，聚合多个子订单但自身不携带主订单号。 */
    MASTER,
    /** 子订单，必须携带所属主订单号。 */
    SUB
}
