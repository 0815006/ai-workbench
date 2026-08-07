package com.realapex.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
