package com.leecardo.paymentdiagnostics.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable order identifier accepted by upstream order systems.
 * <p>订单诊断领域中的标准订单号值对象；构造时会去除首尾空白，并只接受
 * {@code [A-Za-z0-9._-]{1,64}}，确保来自上游订单系统的标识可安全用于查询与关联。</p>
 *
 * @param value stripped identifier containing only letters, digits, dot, underscore, and hyphen；去除首尾空白后的订单号，长度 1 到 64
 */
public record OrderId(String value) {

    private static final Pattern VALID_ORDER_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    /**
     * 校验订单号非空、去除首尾空白，并强制匹配 {@code [A-Za-z0-9._-]{1,64}}。
     */
    public OrderId {
        Objects.requireNonNull(value, "value must not be null");
        value = value.strip();
        if (!VALID_ORDER_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("value must match [A-Za-z0-9._-]{1,64}");
        }
    }
}
