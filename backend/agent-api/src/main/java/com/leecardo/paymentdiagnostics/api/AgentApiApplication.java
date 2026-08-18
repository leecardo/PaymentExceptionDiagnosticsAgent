package com.leecardo.paymentdiagnostics.api;

import com.leecardo.paymentdiagnostics.api.config.DiagnosticUseCaseConfiguration;
import com.leecardo.paymentdiagnostics.infrastructure.ai.AiModelConfiguration;
import com.leecardo.paymentdiagnostics.infrastructure.simulation.SimulationConfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * Spring Boot 应用入口。
 *
 * <p>通过 {@link Import} 导入 AI 模型、确定性模拟数据源和诊断用例装配配置，
 * 让接口层在启动时获得完整的模拟诊断后端依赖。</p>
 */
@SpringBootApplication
@Import({
        AiModelConfiguration.class,
        SimulationConfiguration.class,
        DiagnosticUseCaseConfiguration.class
})
public class AgentApiApplication {

    /**
     * 启动支付异常诊断 API 服务。
     *
     * @param args 命令行启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AgentApiApplication.class, args);
    }
}
