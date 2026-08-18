package com.leecardo.paymentdiagnostics.api.error;

import com.leecardo.paymentdiagnostics.application.order.OrderNotFoundException;
import com.leecardo.paymentdiagnostics.application.port.FactQueryException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain/application exceptions to stable HTTP responses without
 * exposing stack traces, SQL, or filesystem paths.
 *
 * <p>接口层统一异常处理器，将领域层和应用层异常映射为稳定 HTTP 响应。
 * 错误响应绝不暴露堆栈、SQL、文件系统路径、客户身份、手机号、地址、token、secret 等敏感信息。</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 将入参校验失败映射为 {@code 400 Bad Request} 和稳定错误码 {@code INVALID_ORDER_ID}。
     *
     * @param ex 非法参数异常，通常表示订单号格式无效
     * @return 不包含内部细节的错误响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiError> handleInvalidInput(IllegalArgumentException ex) {
        return ResponseEntity
                .badRequest()
                .body(new ApiError("INVALID_ORDER_ID", "orderId is invalid"));
    }

    /**
     * 将订单不存在映射为 {@code 404 Not Found} 和稳定错误码 {@code ORDER_NOT_FOUND}。
     *
     * @param ex 订单查询未命中的应用层异常
     * @return 不包含内部细节的错误响应
     */
    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ApiError> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(new ApiError("ORDER_NOT_FOUND", "order was not found"));
    }

    /**
     * 将诊断事实源查询失败映射为 {@code 503 Service Unavailable}。
     *
     * <p>{@link FactQueryException.Kind#TIMEOUT} 使用错误码 {@code FACT_SOURCE_TIMEOUT}；
     * {@link FactQueryException.Kind#UNAVAILABLE} 使用错误码 {@code FACT_SOURCE_UNAVAILABLE}。
     * 两类错误同属上游事实源不可服务场景，但保留不同稳定错误码便于客户端区分处理。</p>
     *
     * @param ex 事实源超时或不可用异常
     * @return 不包含内部细节的错误响应
     */
    @ExceptionHandler(FactQueryException.class)
    ResponseEntity<ApiError> handleFactQueryException(FactQueryException ex) {
        String code = ex.kind() == FactQueryException.Kind.TIMEOUT
                ? "FACT_SOURCE_TIMEOUT"
                : "FACT_SOURCE_UNAVAILABLE";
        String message = ex.kind() == FactQueryException.Kind.TIMEOUT
                ? "diagnostic fact source timed out"
                : "diagnostic fact source is unavailable";
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ApiError(code, message));
    }
}
