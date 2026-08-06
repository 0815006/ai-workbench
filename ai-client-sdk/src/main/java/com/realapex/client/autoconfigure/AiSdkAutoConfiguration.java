package com.realapex.client.autoconfigure;

import com.realapex.client.client.AiClient;
import com.realapex.client.client.impl.DefaultAiClient;
import com.realapex.client.config.AiConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ai-client-sdk Spring Boot 自动配置。
 * <p>当 classpath 中存在 Spring Boot 且配置了 {@code ai.sdk.api-keys} 时自动生效。
 * 纯 Java 项目（无 Spring）可直接通过 {@link DefaultAiClient#create(AiConfig)} 手动创建。</p>
 */
@Configuration
@EnableConfigurationProperties(AiSdkProperties.class)
@ConditionalOnProperty(prefix = "ai.sdk", name = "api-keys")
public class AiSdkAutoConfiguration {

    /**
     * 自动装配 AiClient Bean。
     *
     * @param properties Spring Boot 配置属性
     * @return AiClient 实例
     */
    @Bean
    @ConditionalOnMissingBean
    public AiClient aiClient(AiSdkProperties properties) {
        AiConfig config = AiConfig.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKeys(properties.getApiKeys())
                .model(properties.getModel())
                .timeout(properties.getTimeout())
                .connectTimeout(properties.getConnectTimeout())
                .maxRetries(properties.getMaxRetries())
                .retryBaseDelay(properties.getRetryBaseDelay())
                .keyBlacklistDuration(properties.getKeyBlacklistDuration())
                .build();
        return DefaultAiClient.create(config);
    }
}
