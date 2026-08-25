package com.realapex.client.provider;

import java.util.Locale;

/**
 * ModelProvider 策略工厂——按提供商名称创建对应适配器。
 * <p>支持三种内置策略：</p>
 * <ul>
 *   <li>{@code openai} — OpenAI 标准 Chat Completions 协议（默认）</li>
 *   <li>{@code deepseek} — DeepSeek 协议（含 reasoning_content 思考链提取）</li>
 *   <li>{@code ollama} — Ollama/vLLM 本地模型（Tool Prompt 注入 + JSON 降级解析）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * ModelProvider provider = ModelProviderFactory.create("deepseek");
 * AiRequest adapted = provider.adaptRequest(request);
 * String reasoning = provider.extractReasoning(response);
 * }</pre>
 */
public final class ModelProviderFactory {

    private ModelProviderFactory() {
    }

    /**
     * 按提供商名称创建策略实例。
     * <p>名称大小写不敏感，未知名称回退为 OpenAI 标准策略。</p>
     *
     * @param providerName 提供商名称（openai / deepseek / ollama）
     * @return ModelProvider 策略实例
     */
    public static ModelProvider create(String providerName) {
        if (providerName == null || providerName.isBlank()) {
            return new OpenAiModelProvider();
        }
        return switch (providerName.toLowerCase(Locale.ROOT)) {
            case "deepseek" -> new DeepSeekModelProvider();
            case "ollama", "vllm", "local" -> new OllamaModelProvider();
            default -> new OpenAiModelProvider();
        };
    }
}