package com.leecardo.paymentdiagnostics.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable order identifier accepted by upstream order systems.
 *
 * @param value stripped identifier containing only letters, digits, dot, underscore, and hyphen
 */
public record OrderId(String value) {

    private static final Pattern VALID_ORDER_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    public OrderId {
        Objects.requireNonNull(value, "value must not be null");
        value = value.strip();
        if (!VALID_ORDER_ID.matcher(value).matches()) {
            throw new IllegalArgumentException("value must match [A-Za-z0-9._-]{1,64}");
        }
    }
}
