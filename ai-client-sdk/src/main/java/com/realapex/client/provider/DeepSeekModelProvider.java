package com.realapex.client.provider;

import com.realapex.client.model.AiResponse;
import com.realapex.client.model.Choice;

/**
 * DeepSeek 协议适配器——深度思考模型（DeepSeek-R1 / V3）专用。
 * <p>DeepSeek 在标准 OpenAI 兼容协议基础上，通过 {@code reasoning_content}\n * 字段返回思考链（Chain-of-Thought）。本适配器负责：</p>
 * <ul>
 *   <li>提取 {@code message.reasoning_content}（非流式）</li>
 *   <li>提取 {@code delta.reasoning_content}（SSE 流式增量）</li>
 *   <li>支持 {@code <thought>} 标签包裹的思考内容降级提取</li>
 * </ul>
 *
 * <h3>典型模型</h3>
 * <ul>
 *   <li>deepseek-chat（V3，通用对话）</li>
 *   <li>deepseek-reasoner（R1，深度思考）</li>
 * </ul>
 */
public class DeepSeekModelProvider implements ModelProvider {

    @Override
    public String providerName() {
        return "deepseek";
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

        // 1. 标准 reasoning_content 字段
        if (choice.getMessage() != null && choice.getMessage().getReasoningContent() != null) {
            return choice.getMessage().getReasoningContent();
        }
        if (choice.getDelta() != null && choice.getDelta().getReasoningContent() != null) {
            return choice.getDelta().getReasoningContent();
        }

        // 2. 降级：从 content 中提取 <thought> 标签包裹的思考内容
        String content = choice.getMessage() != null ? choice.getMessage().getContent() : null;
        if (content == null && choice.getDelta() != null) {
            content = choice.getDelta().getContent();
        }
        if (content != null) {
            int start = content.indexOf("<thought>");
            int end = content.indexOf("</thought>");
            if (start >= 0 && end > start) {
                return content.substring(start + "<thought>".length(), end);
            }
        }
        return null;
    }
}