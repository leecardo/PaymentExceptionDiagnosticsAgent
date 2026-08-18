package com.leecardo.paymentdiagnostics.api.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.leecardo.paymentdiagnostics.application.order.GetOrderUseCase;
import com.leecardo.paymentdiagnostics.domain.OrderRole;
import com.leecardo.paymentdiagnostics.domain.OrderSnapshot;
import com.leecardo.paymentdiagnostics.domain.OrderStatus;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes a safe, desensitised order snapshot for diagnostic queries.
 *
 * <p>模拟订单查询接口控制器。{@code @Profile("simulation")} 表示 {@code GET /api/orders/{orderId}}
 * 端点只在 simulation Profile 激活时存在。</p>
 *
 * <p>控制器只做协议映射：调用订单查询用例并将领域记录转换为响应记录，不承载业务逻辑。
 * 响应记录已脱敏，不包含 customerName、phone、address、token、secret 等客户身份、配送地址或凭证字段。</p>
 */
@RestController
@RequestMapping("/api/orders")
@Profile("simulation")
public class OrderController {

    private final GetOrderUseCase getOrderUseCase;

    /**
     * 注入订单查询用例。
     *
     * @param getOrderUseCase 订单查询用例
     */
    public OrderController(GetOrderUseCase getOrderUseCase) {
        this.getOrderUseCase = getOrderUseCase;
    }

    /**
     * 查询指定订单的脱敏快照。
     *
     * @param orderId 路径中的订单号
     * @return 不含客户身份或配送地址字段的订单响应
     */
    @GetMapping("/{orderId}")
    OrderResponse getOrder(@PathVariable String orderId) {
        OrderSnapshot order = getOrderUseCase.get(orderId);
        return OrderResponse.from(order);
    }

    /**
     * Safe order response — no customer identity, delivery address, or sensitive fields.
     *
     * <p>安全订单响应体，仅暴露诊断所需的订单、商品、金额、来源和状态字段。
     * 不包含 customerName、phone、address、token、secret 等敏感字段。</p>
     *
     * @param orderId 订单号
     * @param masterOrderId 主订单号，子订单场景可能存在
     * @param role 订单角色
     * @param productId 商品标识
     * @param productName 商品名称
     * @param productType 商品类型
     * @param goodsCount 商品数量
     * @param unitPrice 商品单价
     * @param orderAmount 订单金额
     * @param paymentAmount 支付金额
     * @param paymentSource 支付来源
     * @param providerOrderId 渠道订单号
     * @param orderSource 订单来源
     * @param status 订单状态
     * @param orderedAt 下单时间
     * @param stateChangedAt 状态变更时间
     * @param createdAt 记录创建时间
     * @param updatedAt 记录更新时间
     */
    public record OrderResponse(
            String orderId,
            String masterOrderId,
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
         * 使用静态工厂方法完成领域对象到响应记录的映射。
         *
         * <p>该模式把脱敏响应字段集中在一个转换入口，控制器无需复制映射细节，也避免引入业务判断。</p>
         *
         * @param order 领域层订单快照
         * @return 脱敏订单响应
         */
        static OrderResponse from(OrderSnapshot order) {
            return new OrderResponse(
                    order.orderId().value(),
                    order.masterOrderId() == null ? null : order.masterOrderId().value(),
                    order.role(),
                    order.productId(),
                    order.productName(),
                    order.productType(),
                    order.goodsCount(),
                    order.unitPrice(),
                    order.orderAmount(),
                    order.paymentAmount(),
                    order.paymentSource(),
                    order.providerOrderId(),
                    order.orderSource(),
                    order.status(),
                    order.orderedAt(),
                    order.stateChangedAt(),
                    order.createdAt(),
                    order.updatedAt());
        }
    }
}
