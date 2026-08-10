package com.realapex.client.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具定义（OpenAI Function Calling 兼容格式）。
 * <p>描述一个可供大模型调用的工具/函数的 Schema 信息，
 * 作为 {@link AiRequest#tools} 的元素传输给 LLM。</p>
 *
 * <pre>
 * ToolDefinition tool = ToolDefinition.of("get_weather",
 *         "获取指定城市的天气",
 *         Map.of("type", "object", "properties", ...));
 * </pre>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolDefinition {

    /** 工具类型，固定为 "function" */
    @JsonProperty("type")
    @Builder.Default
    private String type = "function";

    /** 函数定义 */
    @JsonProperty("function")
    private FunctionDef function;

    /**
     * 函数定义内部结构。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionDef {
        /** 函数名称 */
        @JsonProperty("name")
        private String name;

        /** 函数描述（供大模型理解用途） */
        @JsonProperty("description")
        private String description;

        /** 参数 JSON Schema */
        @JsonProperty("parameters")
        private Map<String, Object> parameters;
    }

    /**
     * 快捷工厂方法：创建一个标准函数工具定义。
     *
     * @param name        工具名称
     * @param description 工具描述
     * @param jsonSchema  参数 JSON Schema（Jackson 序列化后的 Map 形态）
     * @return ToolDefinition 实例
     */
    public static ToolDefinition of(String name, String description, Map<String, Object> jsonSchema) {
        return ToolDefinition.builder()
                .function(FunctionDef.builder()
                        .name(name)
                        .description(description)
                        .parameters(jsonSchema)
                        .build())
                .build();
    }
}
