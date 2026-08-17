package com.leecardo.paymentdiagnostics.application.port;

import java.util.Objects;

public final class FactQueryException extends RuntimeException {

    private final Kind kind;

    public FactQueryException(Kind kind, String message) {
        super(requireMessage(message));
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
    }

    public Kind kind() {
        return kind;
    }

    private static String requireMessage(String message) {
        Objects.requireNonNull(message, "message must not be null");
        String normalized = message.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        return normalized;
    }

    public enum Kind {
        UNAVAILABLE,
        TIMEOUT
    }
}
