package com.realapex.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chat Completions 消息体，兼容 OpenAI 标准格式。
 * <p>支持 system / user / assistant / tool 四种角色。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Message {

    /** 消息角色：system, user, assistant, tool */
    @JsonProperty("role")
    private String role;

    /** 消息内容文本 */
    @JsonProperty("content")
    private String content;

    /** 工具调用 ID（role=tool 时使用） */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    /**
     * 创建系统消息。
     *
     * @param content 系统提示词
     * @return Message 实例
     */
    public static Message system(String content) {
        return Message.builder().role("system").content(content).build();
    }

    /**
     * 创建用户消息。
     *
     * @param content 用户输入
     * @return Message 实例
     */
    public static Message user(String content) {
        return Message.builder().role("user").content(content).build();
    }

    /**
     * 创建助手消息。
     *
     * @param content 助手回复
     * @return Message 实例
     */
    public static Message assistant(String content) {
        return Message.builder().role("assistant").content(content).build();
    }
}
