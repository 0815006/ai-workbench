package com.realapex.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 大模型标准响应对象，兼容 OpenAI Chat Completions 响应格式。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiResponse {

    /** 响应 ID */
    @JsonProperty("id")
    private String id;

    /** 对象类型 */
    @JsonProperty("object")
    private String object;

    /** 创建时间戳 */
    @JsonProperty("created")
    private long created;

    /** 模型名称 */
    @JsonProperty("model")
    private String model;

    /** 选择项列表 */
    @JsonProperty("choices")
    private List<Choice> choices;

    /** Token 用量 */
    @JsonProperty("usage")
    private Usage usage;

    /** 推理/思考链内容（DeepSeek-R1、o1 等推理模型专用，由 ModelProvider 提取填充） */
    private String reasoningContent;

    /**
     * 提取第一条回复的文本内容。
     *
     * @return 回复文本，无内容时返回空字符串
     */
    public String firstText() {
        if (choices == null || choices.isEmpty()) {
            return "";
        }
        Choice first = choices.get(0);
        if (first.getMessage() != null && first.getMessage().getContent() != null) {
            return first.getMessage().getContent();
        }
        return "";
    }

    /**
     * 获取回复文本（同 {@link #firstText()}）。
     *
     * @return 回复文本
     */
    public String getText() {
        return firstText();
    }

    /**
     * 大模型是否发起了工具调用请求。
     *
     * @return true 如果第一条 choice 包含 tool_calls
     */
    public boolean hasToolCalls() {
        if (choices == null || choices.isEmpty()) {
            return false;
        }
        Choice first = choices.get(0);
        if (first.getMessage() != null
                && first.getMessage().getToolCalls() != null
                && !first.getMessage().getToolCalls().isEmpty()) {
            return true;
        }
        // 也检查 finish_reason
        return "tool_calls".equals(first.getFinishReason());
    }

    /**
     * 获取大模型发起的工具调用列表。
     *
     * @return 工具调用列表，无工具调用时返回空列表
     */
    public List<ToolCall> getToolCalls() {
        if (choices == null || choices.isEmpty()) {
            return Collections.emptyList();
        }
        Choice first = choices.get(0);
        if (first.getMessage() != null && first.getMessage().getToolCalls() != null) {
            return first.getMessage().getToolCalls();
        }
        return Collections.emptyList();
    }

    /**
     * 获取第一个 choice 的 finish_reason。
     *
     * @return finish_reason 字符串，无 choice 时返回 null
     */
    public String getFinishReason() {
        if (choices == null || choices.isEmpty()) {
            return null;
        }
        return choices.get(0).getFinishReason();
    }
}
