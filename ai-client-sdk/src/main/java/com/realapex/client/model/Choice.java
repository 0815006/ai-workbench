package com.realapex.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 大模型返回的单个选择项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Choice {

    /** 序号 */
    @JsonProperty("index")
    private int index;

    /** 消息内容 */
    @JsonProperty("message")
    private Message message;

    /** 流式增量消息（SSE 流中使用） */
    @JsonProperty("delta")
    private Delta delta;

    /** 结束原因：stop, length, tool_calls 等 */
    @JsonProperty("finish_reason")
    private String finishReason;

    /**
     * 流式增量内容。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Delta {
        @JsonProperty("role")
        private String role;

        @JsonProperty("content")
        private String content;

        /** 推理/思考内容（DeepSeek-R1、o1 等推理模型专用） */
        @JsonProperty("reasoning_content")
        private String reasoningContent;
    }
}
