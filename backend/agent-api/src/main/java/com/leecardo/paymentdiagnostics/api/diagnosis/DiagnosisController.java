package com.leecardo.paymentdiagnostics.api.diagnosis;

import java.time.Instant;
import java.util.List;

import com.leecardo.paymentdiagnostics.application.diagnosis.DiagnosePaymentExceptionUseCase;
import com.leecardo.paymentdiagnostics.domain.DataMode;
import com.leecardo.paymentdiagnostics.domain.DiagnosisEvidence;
import com.leecardo.paymentdiagnostics.domain.DiagnosisResult;
import com.leecardo.paymentdiagnostics.domain.DiagnosisRuleId;
import com.leecardo.paymentdiagnostics.domain.DiagnosisStage;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes a deterministic diagnosis result for a given order.
 *
 * <p>模拟诊断接口控制器。{@code @Profile("simulation")} 表示 {@code GET /api/diagnoses/orders/{orderId}}
 * 端点只在 simulation Profile 激活时存在。</p>
 *
 * <p>控制器只做协议映射：调用支付异常诊断用例并将领域诊断结果转换为响应记录，不承载业务逻辑。
 * 响应记录仅包含 orderId、dataMode、stage、ruleId、summary、evidence、warnings，
 * 不包含 customerName、phone、address、token、secret 等敏感字段。</p>
 */
@RestController
@RequestMapping("/api/diagnoses/orders")
@Profile("simulation")
public class DiagnosisController {

    private final DiagnosePaymentExceptionUseCase diagnoseUseCase;

    /**
     * 注入支付异常诊断用例。
     *
     * @param diagnoseUseCase 支付异常诊断用例
     */
    public DiagnosisController(DiagnosePaymentExceptionUseCase diagnoseUseCase) {
        this.diagnoseUseCase = diagnoseUseCase;
    }

    /**
     * 诊断指定订单的支付异常阶段和证据。
     *
     * @param orderId 路径中的订单号
     * @return 包含 {@code orderId/dataMode/stage/ruleId/summary/evidence/warnings} 的诊断响应
     */
    @GetMapping("/{orderId}")
    DiagnosisResponse diagnose(@PathVariable String orderId) {
        DiagnosisResult result = diagnoseUseCase.diagnose(orderId);
        return DiagnosisResponse.from(result);
    }

    /**
     * Safe diagnosis response — evidence references are traceable but non-sensitive.
     *
     * <p>安全诊断响应体，证据引用可追踪但不携带客户身份、联系方式、配送地址、token、secret 等敏感字段。</p>
     *
     * @param orderId 订单号
     * @param dataMode 数据模式
     * @param stage 诊断阶段
     * @param ruleId 命中的诊断规则
     * @param summary 诊断摘要
     * @param evidence 诊断证据列表
     * @param warnings 诊断警告列表
     */
    public record DiagnosisResponse(
            String orderId,
            DataMode dataMode,
            DiagnosisStage stage,
            DiagnosisRuleId ruleId,
            String summary,
            List<EvidenceResponse> evidence,
            List<String> warnings) {

        /**
         * 使用静态工厂方法完成领域诊断结果到响应记录的映射。
         *
         * <p>该模式将响应字段选择集中在一个转换入口，控制器只负责编排协议调用，不复制业务规则。</p>
         *
         * @param result 领域层诊断结果
         * @return 安全诊断响应
         */
        static DiagnosisResponse from(DiagnosisResult result) {
            return new DiagnosisResponse(
                    result.orderId().value(),
                    result.dataMode(),
                    result.stage(),
                    result.ruleId(),
                    result.summary(),
                    result.evidence().stream().map(EvidenceResponse::from).toList(),
                    result.warnings());
        }
    }

    /**
     * 诊断证据响应项。
     *
     * <p>仅返回证据标识、来源、摘要和观测时间，避免泄露敏感原始事实。</p>
     *
     * @param id 证据标识
     * @param source 证据来源
     * @param summary 证据摘要
     * @param observedAt 证据观测时间
     */
    public record EvidenceResponse(
            String id,
            String source,
            String summary,
            Instant observedAt) {

        /**
         * 使用静态工厂方法完成领域证据到响应记录的映射。
         *
         * @param evidence 领域层诊断证据
         * @return 证据响应项
         */
        static EvidenceResponse from(DiagnosisEvidence evidence) {
            return new EvidenceResponse(
                    evidence.id(),
                    evidence.source(),
                    evidence.summary(),
                    evidence.observedAt());
        }
    }
}
