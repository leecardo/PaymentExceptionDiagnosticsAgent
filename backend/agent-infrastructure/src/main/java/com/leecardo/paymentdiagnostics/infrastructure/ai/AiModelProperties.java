package com.leecardo.paymentdiagnostics.infrastructure.ai;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.ai} 配置属性。
 * <p>
 * 包含 AI 开关、OpenAI 兼容服务地址、API Key 和模型名称，供模型配置类在启用时装配客户端。
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiModelProperties {

    private boolean enabled;
    private URI baseUrl;
    private String apiKey;
    private String modelName;

    /**
     * 返回是否启用 AI 模型客户端装配。
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用 AI 模型客户端装配。
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 返回 OpenAI 兼容服务的基础地址。
     */
    public URI getBaseUrl() {
        return baseUrl;
    }

    /**
     * 设置 OpenAI 兼容服务的基础地址。
     */
    public void setBaseUrl(URI baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 返回访问 OpenAI 兼容服务的 API Key。
     */
    public String getApiKey() {
        return apiKey;
    }

    /**
     * 设置访问 OpenAI 兼容服务的 API Key。
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * 返回请求使用的模型名称。
     */
    public String getModelName() {
        return modelName;
    }

    /**
     * 设置请求使用的模型名称。
     */
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
}
