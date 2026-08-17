package com.leecardo.paymentdiagnostics.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OrderIdTest {

    @Test
    void stripsSurroundingWhitespace() {
        OrderId orderId = new OrderId(" order-123_ABC.01 \n");

        assertEquals("order-123_ABC.01", orderId.value());
    }

    @Test
    void rejectsBlankValue() {
        assertThrows(IllegalArgumentException.class, () -> new OrderId(" \t\n"));
    }

    @Test
    void rejectsValueLongerThanSixtyFourCharacters() {
        assertThrows(IllegalArgumentException.class,
                () -> new OrderId("A".repeat(65)));
    }

    @Test
    void rejectsIllegalCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new OrderId("order/123"));
    }
}
