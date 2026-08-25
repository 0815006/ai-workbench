package com.realapex.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Chat Completions 消息体，兼容 OpenAI 标准格式。
 * <p>支持 system / user / assistant / tool 四种角色，
 * 以及 assistant 角色携带的 tool_calls 列表。</p>
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

    /** 推理/思考链内容（DeepSeek-R1、o1 等推理模型专用，非流式响应） */
    @JsonProperty("reasoning_content")
    private String reasoningContent;

    /** 工具调用列表（role=assistant 且发起 function call 时使用） */
    @JsonProperty("tool_calls")
    private List<ToolCall> toolCalls;

    /** 工具调用 ID（role=tool 时使用，关联对应的 assistant tool_call） */
    @JsonProperty("tool_call_id")
    private String toolCallId;

    /** 工具名称（role=tool 时可选，标识回传结果的工具名） */
    @JsonProperty("name")
    private String name;

    // ==================== 静态工厂方法 ====================

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
     * 创建助手消息（纯文本回复）。
     *
     * @param content 助手回复
     * @return Message 实例
     */
    public static Message assistant(String content) {
        return Message.builder().role("assistant").content(content).build();
    }

    /**
     * 创建带工具调用请求的助手消息（单个工具调用）。
     *
     * @param id        工具调用唯一 ID
     * @param name      工具函数名称
     * @param arguments 参数 JSON 字符串
     * @return Message 实例
     */
    public static Message assistantToolCall(String id, String name, String arguments) {
        return Message.builder()
                .role("assistant")
                .content(null)
                .toolCalls(List.of(ToolCall.of(id, name, arguments)))
                .build();
    }

    /**
     * 创建带多个工具调用请求的助手消息。
     *
     * @param toolCalls 工具调用列表
     * @return Message 实例
     */
    public static Message assistantWithToolCalls(List<ToolCall> toolCalls) {
        return Message.builder()
                .role("assistant")
                .content(null)
                .toolCalls(toolCalls)
                .build();
    }

    /**
     * 创建工具执行结果消息（role=tool）。
     *
     * @param toolCallId 对应的工具调用 ID
     * @param content    工具执行结果文本
     * @return Message 实例
     */
    public static Message toolResult(String toolCallId, String content) {
        return Message.builder()
                .role("tool")
                .toolCallId(toolCallId)
                .content(content)
                .build();
    }

    /**
     * 创建带工具名的工具执行结果消息。
     *
     * @param toolCallId 对应的工具调用 ID
     * @param name       工具名称
     * @param content    工具执行结果文本
     * @return Message 实例
     */
    public static Message toolResult(String toolCallId, String name, String content) {
        return Message.builder()
                .role("tool")
                .toolCallId(toolCallId)
                .name(name)
                .content(content)
                .build();
    }
}
