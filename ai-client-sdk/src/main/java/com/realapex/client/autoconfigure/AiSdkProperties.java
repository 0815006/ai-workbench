package com.realapex.client.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Spring Boot 配置属性绑定。
 * <p>业务方在 {@code application.yml} 中以 {@code ai.sdk} 为前缀配置即可。</p>
 *
 * <pre>
 * ai:
 *   sdk:
 *     base-url: https://api.deepseek.com/v1
 *     api-keys:
 *       - sk-xxx
 *       - sk-yyy
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "ai.sdk")
public class AiSdkProperties {

    /** API 端点 Base URL */
    private String baseUrl = "https://api.deepseek.com/v1";

    /** API Key 列表 */
    private List<String> apiKeys;

    /** 默认模型 */
    private String model = "deepseek-chat";

    /** 请求超时 */
    private Duration timeout = Duration.ofSeconds(60);

    /** 连接超时 */
    private Duration connectTimeout = Duration.ofSeconds(10);

    /** 最大重试次数 */
    private int maxRetries = 3;

    /** 重试基础延迟 */
    private Duration retryBaseDelay = Duration.ofSeconds(1);

    /** Key 黑名单隔离时长 */
    private Duration keyBlacklistDuration = Duration.ofMinutes(10);
}
