package com.realapex.client.provider;

import com.realapex.client.model.AiResponse;
import com.realapex.client.model.Choice;

/**
 * OpenAI 标准 Chat Completions 协议适配器。
 * <p>作为默认策略，直接使用标准 OpenAI 兼容协议，无需请求改写。
 * 支持 o1/o3 等推理模型的 {@code reasoning_content} 思考链提取。</p>
 *
 * <h3>适配范围</h3>
 * <ul>
 *   <li>OpenAI GPT-4o / GPT-4.1 系列</li>
 *   <li>OpenAI o1 / o3 推理系列（reasoning_content）</li>
 *   <li>任何严格遵循 OpenAI Chat Completions 协议的兼容服务</li>
 * </ul>
 */
public class OpenAiModelProvider implements ModelProvider {

    @Override
    public String providerName() {
        return "openai";
    }

    @Override
    public boolean supportsReasoning() {
        return true;
    }

    @Override
    public String extractReasoning(AiResponse response) {
        if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
            return null;
        }
        Choice choice = response.getChoices().get(0);
        if (choice.getMessage() != null) {
            return choice.getMessage().getReasoningContent();
        }
        if (choice.getDelta() != null) {
            return choice.getDelta().getReasoningContent();
        }
        return null;
    }
}