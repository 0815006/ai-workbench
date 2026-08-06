package com.realapex.client.config;

import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.util.List;

/**
 * SDK 全局配置模型。
 * <p>包含 API 端点、Key 列表、超时、重试等所有可配参数，均提供合理默认值。</p>
 */
@Data
@Builder
public class AiConfig {

    /** API 端点 Base URL（兼容 OpenAI 格式） */
    @Builder.Default
    private String baseUrl = "https://api.deepseek.com/v1";

    /** API Key 列表（支持多 Key 轮询） */
    private List<String> apiKeys;

    /** 默认模型 */
    @Builder.Default
    private String model = "deepseek-chat";

    /** 请求超时时间 */
    @Builder.Default
    private Duration timeout = Duration.ofSeconds(60);

    /** 连接超时时间 */
    @Builder.Default
    private Duration connectTimeout = Duration.ofSeconds(10);

    /** 遇 429/5xx 最大重试次数 */
    @Builder.Default
    private int maxRetries = 3;

    /** 重试退避基础延迟 */
    @Builder.Default
    private Duration retryBaseDelay = Duration.ofSeconds(1);

    /** Key 黑名单隔离时长（401/402 后临时禁用） */
    @Builder.Default
    private Duration keyBlacklistDuration = Duration.ofMinutes(10);
}
