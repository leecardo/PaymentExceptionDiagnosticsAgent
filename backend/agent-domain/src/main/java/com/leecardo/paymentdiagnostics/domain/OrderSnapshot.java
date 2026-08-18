package com.leecardo.paymentdiagnostics.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of order facts used by payment exception diagnostics.
 * <p>支付异常诊断使用的不可变订单事实快照，承载订单商品、金额、支付渠道和状态时间线。
 * 构造时维护领域不变量：商品数量必须大于 0，金额不能为负，更新时间不能早于创建时间，
 * 且只有子订单允许并且必须携带主订单号。</p>
 *
 * @param orderId 当前被诊断订单号
 * @param masterOrderId 子订单所属主订单号；仅 {@link OrderRole#SUB} 可填写
 * @param role 订单在单订单/主订单/子订单结构中的角色
 * @param productId 商品标识，不能为空白
 * @param productName 商品名称，不能为空白
 * @param productType 商品类型，不能为空白
 * @param goodsCount 商品数量，必须大于 0
 * @param unitPrice 商品单价，必须非负
 * @param orderAmount 订单应付金额，必须非负
 * @param paymentAmount 实际支付金额，必须非负
 * @param paymentSource 支付来源或支付方式，可为空；空白会规范化为 {@code null}
 * @param providerOrderId 第三方支付单号，可为空；空白会规范化为 {@code null}
 * @param orderSource 订单来源，不能为空白
 * @param status 当前订单状态
 * @param orderedAt 下单时间，不能为空
 * @param stateChangedAt 订单状态最近变更时间，可为空
 * @param createdAt 订单记录创建时间，不能为空
 * @param updatedAt 订单记录更新时间，不能早于创建时间
 */
public record OrderSnapshot(
        OrderId orderId,
        OrderId masterOrderId,
        OrderRole role,
        String productId,
        String productName,
        String productType,
        int goodsCount,
        BigDecimal unitPrice,
        BigDecimal orderAmount,
        BigDecimal paymentAmount,
        String paymentSource,
        String providerOrderId,
        String orderSource,
        OrderStatus status,
        Instant orderedAt,
        Instant stateChangedAt,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * 规范化文本字段，校验数量、金额和时间顺序，并强制主子订单关系与 {@link OrderRole} 一致。
     */
    public OrderSnapshot {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        productId = requireText(productId, "productId");
        productName = requireText(productName, "productName");
        productType = requireText(productType, "productType");
        if (goodsCount <= 0) {
            throw new IllegalArgumentException("goodsCount must be greater than zero");
        }
        requireNonNegative(unitPrice, "unitPrice");
        requireNonNegative(orderAmount, "orderAmount");
        requireNonNegative(paymentAmount, "paymentAmount");
        paymentSource = normalizeOptionalText(paymentSource);
        providerOrderId = normalizeOptionalText(providerOrderId);
        orderSource = requireText(orderSource, "orderSource");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(orderedAt, "orderedAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (role == OrderRole.SUB) {
            if (masterOrderId == null) {
                throw new IllegalArgumentException("SUB orders must have a masterOrderId");
            }
        } else if (masterOrderId != null) {
            throw new IllegalArgumentException("SINGLE and MASTER orders must not have a masterOrderId");
        }
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeOptionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void requireNonNegative(BigDecimal value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
