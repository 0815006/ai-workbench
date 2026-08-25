package com.realapex.client.provider;

import com.realapex.client.model.AiRequest;
import com.realapex.client.model.AiResponse;

/**
 * 模型提供商策略接口——多厂商适配的统一抽象。
 * <p>将 OpenAI 标准 API、DeepSeek（含 Reasoning/思考链）及 Ollama/vLLM 等
 * 本地模型的协议差异封装为可插拔策略，{@code DefaultAiClient} 通过
 * {@link ModelProviderFactory} 按配置选择对应实现。</p>
 *
 * <h3>适配点</h3>
 * <ul>
 *   <li><b>请求适配</b>：{@link #adaptRequest} — 注入厂商特有字段（如 Ollama 的 Tool Prompt）</li>
 *   <li><b>响应解析</b>：{@link #parseResponse} — 兼容非标准 JSON / 降级解析</li>
 *   <li><b>推理内容</b>：{@link #extractReasoning} — 提取 DeepSeek-R1 的 reasoning_content</li>
 * </ul>
 *
 * <h3>内置实现</h3>
 * <ul>
 *   <li>{@link OpenAiModelProvider} — OpenAI 标准 Chat Completions 协议</li>
 *   <li>{@link DeepSeekModelProvider} — DeepSeek 协议（含 reasoning_content 思考链提取）</li>
 *   <li>{@link OllamaModelProvider} — Ollama/vLLM 本地模型（Tool Prompt 注入 + JSON 降级解析）</li>
 * </ul>
 */
public interface ModelProvider {

    /**
     * 提供商名称（用于配置识别与日志）。
     *
     * @return 提供商名称，如 "openai" / "deepseek" / "ollama"
     */
    String providerName();

    /**
     * 是否支持推理/思考链内容（reasoning_content）。
     * <p>DeepSeek-R1、o1 等推理模型返回 true，普通模型返回 false。</p>
     *
     * @return true 表示支持推理内容提取
     */
    boolean supportsReasoning();

    /**
     * 请求适配——在发送前注入厂商特有字段。
     * <p>默认实现直接返回原请求。Ollama 等本地模型可在此注入 Tool Prompt 提示词。</p>
     *
     * @param request 原始请求
     * @return 适配后的请求
     */
    default AiRequest adaptRequest(AiRequest request) {
        return request;
    }

    /**
     * 响应解析——将厂商原始响应体解析为统一 {@link AiResponse}。
     * <p>默认实现不做处理（由 DefaultAiClient 的标准 Jackson 解析完成）。
     * 非标准厂商可在此做 JSON 降级解析。</p>
     *
     * @param rawBody 厂商原始响应体（JSON 字符串）
     * @return 解析后的 AiResponse，无法解析时返回 null（回退标准解析）
     */
    default AiResponse parseResponse(String rawBody) {
        return null;
    }

    /**
     * 推理内容提取——从响应中提取思考链文本。
     * <p>DeepSeek 的 reasoning_content 位于 message 层，OpenAI 的 o1 系列
     * 位于 delta.reasoning_content。各厂商实现按自身协议提取。</p>
     *
     * @param response 已解析的统一响应
     * @return 推理内容文本，无推理内容时返回 null
     */
    default String extractReasoning(AiResponse response) {
        return null;
    }
}