package com.realapex.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token 用量统计。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Usage {

    /** 提示词 token 数 */
    @JsonProperty("prompt_tokens")
    private int promptTokens;

    /** 生成 token 数 */
    @JsonProperty("completion_tokens")
    private int completionTokens;

    /** 总 token 数 */
    @JsonProperty("total_tokens")
    private int totalTokens;
}
