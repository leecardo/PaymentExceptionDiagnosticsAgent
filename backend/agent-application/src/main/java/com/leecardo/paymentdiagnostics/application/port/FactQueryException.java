package com.leecardo.paymentdiagnostics.application.port;

import java.util.Objects;

/**
 * 事实查询异常，表示出站端口无法可靠返回诊断事实而非业务记录缺失。
 */
public final class FactQueryException extends RuntimeException {

    private final Kind kind;

    /**
     * 创建事实查询异常，并记录失败类型与可读错误信息。
     *
     * @param kind 查询失败类型
     * @param message 查询失败原因；不能为空白
     * @throws IllegalArgumentException 当 message 为空白时抛出
     */
    public FactQueryException(Kind kind, String message) {
        super(requireMessage(message));
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
    }

    /**
     * 返回查询失败类型，用于区分依赖不可用和查询超时。
     *
     * @return 查询失败类型
     */
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

    /**
     * 事实查询失败分类：UNAVAILABLE 表示下游不可用，TIMEOUT 表示查询超过时限。
     */
    public enum Kind {
        /** 下游事实来源不可用，诊断流程无法信任该类事实。 */
        UNAVAILABLE,
        /** 查询超过允许时限，诊断流程无法判断事实是否缺失。 */
        TIMEOUT
    }
}
