package com.leecardo.paymentdiagnostics.infrastructure.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * AI 模型基础设施配置类。
 * <p>
 * 当 {@code app.ai.enabled=true} 时创建 OpenAI 兼容的 {@link ChatModel}，
 * 并在基础地址、API Key 或模型名缺失时让应用启动失败。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiModelProperties.class)
public class AiModelConfiguration {

    /**
     * 根据 {@link AiModelProperties} 创建 OpenAI 兼容聊天模型 Bean。
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.ai", name = "enabled", havingValue = "true")
    ChatModel openAiCompatibleChatModel(AiModelProperties properties) {
        requireConfigured(properties.getBaseUrl(), "app.ai.base-url");
        requireConfigured(properties.getApiKey(), "app.ai.api-key");
        requireConfigured(properties.getModelName(), "app.ai.model-name");

        return OpenAiChatModel.builder()
                .baseUrl(properties.getBaseUrl().toString())
                .apiKey(properties.getApiKey())
                .modelName(properties.getModelName())
                .build();
    }

    /**
     * 校验启用 AI 时必填配置已经提供；缺失或空白字符串会抛出启动期异常。
     */
    private static void requireConfigured(Object value, String propertyName) {
        if (value instanceof String text) {
            if (!StringUtils.hasText(text)) {
                throw new IllegalStateException(propertyName + " is required when app.ai.enabled=true");
            }
            return;
        }
        if (value == null) {
            throw new IllegalStateException(propertyName + " is required when app.ai.enabled=true");
        }
    }
}
