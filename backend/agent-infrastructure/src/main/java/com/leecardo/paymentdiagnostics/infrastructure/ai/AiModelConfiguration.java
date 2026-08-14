package com.leecardo.paymentdiagnostics.infrastructure.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AiModelProperties.class)
public class AiModelConfiguration {

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
