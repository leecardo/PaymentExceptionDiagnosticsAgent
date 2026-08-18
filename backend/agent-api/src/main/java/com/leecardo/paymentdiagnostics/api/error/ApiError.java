package com.leecardo.paymentdiagnostics.api.error;

/**
 * Stable, non-sensitive error response body.
 *
 * <p>稳定、非敏感的错误响应体。对外仅暴露客户端可依赖的错误码和可读描述，
 * 不携带堆栈、SQL、文件系统路径或敏感业务字段。</p>
 *
 * @param code stable error code consumed by API clients
 * @param message human-readable error description without internal details
 * @param code 客户端可稳定消费的错误码
 * @param message 不包含内部实现细节的可读错误描述
 */
public record ApiError(String code, String message) {
}
