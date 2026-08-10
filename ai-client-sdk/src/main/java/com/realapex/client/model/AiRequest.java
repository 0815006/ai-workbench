package com.realapex.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 大模型统一请求对象，兼容 OpenAI Chat Completions 协议。
 * <p>支持所有 OpenAI 兼容 API 的标准参数，包含 Tool Calling 与 ToolChoice 策略配置，
 * 可通过 {@link #extraBody} 扩展厂商特有字段。</p>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiRequest {

    /** 模型名称（如 deepseek-chat、gpt-4o） */
    @JsonProperty("model")
    private String model;

    /** 消息列表 */
    @JsonProperty("messages")
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    /** 采样温度，0-2，默认 0.7 */
    @JsonProperty("temperature")
    private Double temperature;

    /** Top-P 核采样，0-1 */
    @JsonProperty("top_p")
    private Double topP;

    /** 最大生成 token 数 */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /** 是否启用 JSON 模式输出 */
    @JsonProperty("response_format")
    private ResponseFormat responseFormat;

    /** 停止词列表 */
    @JsonProperty("stop")
    private List<String> stop;

    /** 是否流式输出 */
    @JsonProperty("stream")
    private Boolean stream;

    /** 可用工具列表（Function Calling） */
    @JsonProperty("tools")
    private List<ToolDefinition> tools;

    /** 工具选择策略：auto / none / required / 或指定函数 */
    @JsonProperty("tool_choice")
    private Object toolChoice;

    /** 扩展字段（厂商特有参数，如 top_k 等） */
    @JsonProperty("extra_body")
    @Builder.Default
    private Map<String, Object> extraBody = new HashMap<>();

    /**
     * 响应格式约束。
     */
    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseFormat {
        @JsonProperty("type")
        private String type;

        /**
         * 创建 JSON 对象模式约束。
         */
        public static ResponseFormat jsonObject() {
            return ResponseFormat.builder().type("json_object").build();
        }
    }

}
