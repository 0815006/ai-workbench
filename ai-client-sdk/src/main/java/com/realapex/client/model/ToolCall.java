package com.realapex.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具调用指令（大模型返回的 function call）。
 * <p>当大模型决定调用某个工具时，会在响应中返回一个或多个 ToolCall，
 * 包含工具名称和序列化为 JSON 字符串的参数。</p>
 *
 * <p>同时用于：
 * <ul>
 *   <li>{@link Message#toolCalls} — 助手消息中的完整工具调用记录</li>
 *   <li>{@link Choice.Delta#toolCalls} — SSE 流式增量中的工具调用片段</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ToolCall {

    /** 工具调用唯一 ID（用于后续 toolResult 关联） */
    @JsonProperty("id")
    private String id;

    /** 调用类型，固定为 "function" */
    @JsonProperty("type")
    @Builder.Default
    private String type = "function";

    /** 函数调用详情 */
    @JsonProperty("function")
    private FunctionCall function;

    /**
     * 内部函数调用结构。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FunctionCall {
        /** 函数名称 */
        @JsonProperty("name")
        private String name;

        /** 函数参数（JSON 字符串） */
        @JsonProperty("arguments")
        private String arguments;
    }

    /**
     * 获取工具名称。
     *
     * @return 工具名称，function 为 null 时返回 null
     */
    public String getName() {
        return function != null ? function.getName() : null;
    }

    /**
     * 获取参数 JSON 字符串。
     *
     * @return 参数 JSON，function 为 null 时返回 null
     */
    public String getArguments() {
        return function != null ? function.getArguments() : null;
    }

    /**
     * 快捷工厂方法。
     *
     * @param id        调用唯一 ID
     * @param name      函数名称
     * @param arguments 参数 JSON 字符串
     * @return ToolCall 实例
     */
    public static ToolCall of(String id, String name, String arguments) {
        return ToolCall.builder()
                .id(id)
                .function(FunctionCall.builder()
                        .name(name)
                        .arguments(arguments)
                        .build())
                .build();
    }
}
