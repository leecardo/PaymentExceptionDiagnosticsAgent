package com.leecardo.paymentdiagnostics.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class OrderSnapshotTest {

    private static final Instant ORDERED_AT = Instant.parse("2026-08-14T10:00:00Z");
    private static final Instant STATE_CHANGED_AT = Instant.parse("2026-08-14T10:05:00Z");
    private static final Instant CREATED_AT = Instant.parse("2026-08-14T10:01:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-14T10:06:00Z");

    @Test
    void stripsRequiredAndOptionalText() {
        OrderSnapshot snapshot = snapshot(
                new OrderId(" order-123 "),
                null,
                OrderRole.SINGLE,
                " product-1 ",
                " Payment Exception Course ",
                " digital-course ",
                2,
                new BigDecimal("19.90"),
                new BigDecimal("39.80"),
                new BigDecimal("39.80"),
                " alipay ",
                " provider-456 ",
                " web ",
                OrderStatus.PAID,
                ORDERED_AT,
                STATE_CHANGED_AT,
                CREATED_AT,
                UPDATED_AT);

        assertEquals("order-123", snapshot.orderId().value());
        assertEquals("product-1", snapshot.productId());
        assertEquals("Payment Exception Course", snapshot.productName());
        assertEquals("digital-course", snapshot.productType());
        assertEquals("alipay", snapshot.paymentSource());
        assertEquals("provider-456", snapshot.providerOrderId());
        assertEquals("web", snapshot.orderSource());
    }

    @Test
    void normalizesBlankOptionalTextAndAllowsMissingStateChangedAt() {
        OrderSnapshot snapshot = snapshot(
                new OrderId("order-123"),
                null,
                OrderRole.SINGLE,
                "product-1",
                "Payment Exception Course",
                "digital-course",
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                " ",
                "\t",
                "web",
                OrderStatus.PENDING_PAYMENT,
                ORDERED_AT,
                null,
                CREATED_AT,
                UPDATED_AT);

        assertNull(snapshot.paymentSource());
        assertNull(snapshot.providerOrderId());
        assertNull(snapshot.stateChangedAt());
    }

    @Test
    void rejectsMissingRequiredValueFields() {
        assertThrows(NullPointerException.class, () -> snapshotWithAmounts(
                null, BigDecimal.ZERO, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> snapshotWithAmounts(
                BigDecimal.ZERO, null, BigDecimal.ZERO));
        assertThrows(NullPointerException.class, () -> snapshotWithAmounts(
                BigDecimal.ZERO, BigDecimal.ZERO, null));
    }

    @Test
    void rejectsMissingStatus() {
        assertThrows(NullPointerException.class, () -> snapshot(
                new OrderId("order-123"),
                null,
                OrderRole.SINGLE,
                "product-1",
                "Payment Exception Course",
                "digital-course",
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                "web",
                null,
                ORDERED_AT,
                null,
                CREATED_AT,
                UPDATED_AT));
    }

    @Test
    void rejectsMissingRequiredTemporalFields() {
        assertThrows(NullPointerException.class, () -> snapshotWithTimes(null, CREATED_AT, UPDATED_AT));
        assertThrows(NullPointerException.class, () -> snapshotWithTimes(ORDERED_AT, null, UPDATED_AT));
        assertThrows(NullPointerException.class, () -> snapshotWithTimes(ORDERED_AT, CREATED_AT, null));
    }

    @Test
    void rejectsBlankRequiredText() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                new OrderId("order-123"),
                null,
                OrderRole.SINGLE,
                " ",
                "Payment Exception Course",
                "digital-course",
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                "web",
                OrderStatus.PENDING_PAYMENT,
                ORDERED_AT,
                null,
                CREATED_AT,
                UPDATED_AT));
    }

    @Test
    void rejectsNegativeUnitPrice() {
        assertThrows(IllegalArgumentException.class, () -> snapshotWithAmounts(
                new BigDecimal("-0.01"), BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeOrderAmount() {
        assertThrows(IllegalArgumentException.class, () -> snapshotWithAmounts(
                BigDecimal.ZERO, new BigDecimal("-0.01"), BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativePaymentAmount() {
        assertThrows(IllegalArgumentException.class, () -> snapshotWithAmounts(
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("-0.01")));
    }

    @Test
    void rejectsGoodsCountAtOrBelowZero() {
        assertThrows(IllegalArgumentException.class, () -> snapshotWithGoodsCount(0));
        assertThrows(IllegalArgumentException.class, () -> snapshotWithGoodsCount(-1));
    }

    @Test
    void rejectsUpdatedAtBeforeCreatedAt() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                new OrderId("order-123"),
                null,
                OrderRole.SINGLE,
                "product-1",
                "Payment Exception Course",
                "digital-course",
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                "web",
                OrderStatus.PENDING_PAYMENT,
                ORDERED_AT,
                null,
                CREATED_AT,
                CREATED_AT.minusSeconds(1)));
    }

    @Test
    void rejectsSubOrderWithoutMasterOrderId() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                new OrderId("order-123"),
                null,
                OrderRole.SUB,
                "product-1",
                "Payment Exception Course",
                "digital-course",
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                "web",
                OrderStatus.PENDING_PAYMENT,
                ORDERED_AT,
                null,
                CREATED_AT,
                UPDATED_AT));
    }

    @Test
    void acceptsSubOrderWithMasterOrderId() {
        OrderSnapshot snapshot = snapshot(
                new OrderId("sub-123"),
                new OrderId("master-123"),
                OrderRole.SUB,
                "product-1",
                "Payment Exception Course",
                "digital-course",
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                "web",
                OrderStatus.PENDING_PAYMENT,
                ORDERED_AT,
                null,
                CREATED_AT,
                UPDATED_AT);

        assertEquals("master-123", snapshot.masterOrderId().value());
    }

    @Test
    void rejectsSingleOrderWithMasterOrderId() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                new OrderId("order-123"),
                new OrderId("master-123"),
                OrderRole.SINGLE,
                "product-1",
                "Payment Exception Course",
                "digital-course",
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                "web",
                OrderStatus.PENDING_PAYMENT,
                ORDERED_AT,
                null,
                CREATED_AT,
                UPDATED_AT));
    }

    @Test
    void rejectsMasterOrderWithMasterOrderId() {
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                new OrderId("order-123"),
                new OrderId("master-123"),
                OrderRole.MASTER,
                "product-1",
                "Payment Exception Course",
                "digital-course",
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                "web",
                OrderStatus.PENDING_PAYMENT,
                ORDERED_AT,
                null,
                CREATED_AT,
                UPDATED_AT));
    }

    private static void snapshotWithAmounts(BigDecimal unitPrice, BigDecimal orderAmount, BigDecimal paymentAmount) {
        snapshot(
                new OrderId("order-123"),
                null,
                OrderRole.SINGLE,
                "product-1",
                "Payment Exception Course",
                "digital-course",
                1,
                unitPrice,
                orderAmount,
                paymentAmount,
                null,
                null,
                "web",
                OrderStatus.PENDING_PAYMENT,
                ORDERED_AT,
                null,
                CREATED_AT,
                UPDATED_AT);
    }

    private static void snapshotWithGoodsCount(int goodsCount) {
        snapshot(
                new OrderId("order-123"),
                null,
                OrderRole.SINGLE,
                "product-1",
                "Payment Exception Course",
                "digital-course",
                goodsCount,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                "web",
                OrderStatus.PENDING_PAYMENT,
                ORDERED_AT,
                null,
                CREATED_AT,
                UPDATED_AT);
    }

    private static void snapshotWithTimes(Instant orderedAt, Instant createdAt, Instant updatedAt) {
        snapshot(
                new OrderId("order-123"),
                null,
                OrderRole.SINGLE,
                "product-1",
                "Payment Exception Course",
                "digital-course",
                1,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                null,
                "web",
                OrderStatus.PENDING_PAYMENT,
                orderedAt,
                null,
                createdAt,
                updatedAt);
    }

    private static OrderSnapshot snapshot(
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
        return new OrderSnapshot(
                orderId,
                masterOrderId,
                role,
                productId,
                productName,
                productType,
                goodsCount,
                unitPrice,
                orderAmount,
                paymentAmount,
                paymentSource,
                providerOrderId,
                orderSource,
                status,
                orderedAt,
                stateChangedAt,
                createdAt,
                updatedAt);
    }
}
