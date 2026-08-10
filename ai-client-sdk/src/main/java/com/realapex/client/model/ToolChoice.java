package com.realapex.client.model;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具选择策略，控制大模型是否/如何调用工具。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 模型自动决定
 * AiRequest.builder().toolChoice(ToolChoice.AUTO).build();
 *
 * // 强制不调用工具
 * AiRequest.builder().toolChoice(ToolChoice.NONE).build();
 *
 * // 强制调用工具
 * AiRequest.builder().toolChoice(ToolChoice.REQUIRED).build();
 *
 * // 强制调用指定工具
 * AiRequest.builder().toolChoice(ToolChoice.function("get_weather")).build();
 * }</pre>
 */
@JsonSerialize(using = ToolChoice.ToolChoiceSerializer.class)
@JsonDeserialize(using = ToolChoice.ToolChoiceDeserializer.class)
public class ToolChoice {

    /** 模型自动决定（默认） */
    public static final ToolChoice AUTO = new ToolChoice("auto", null);

    /** 禁止调用任何工具 */
    public static final ToolChoice NONE = new ToolChoice("none", null);

    /** 强制调用至少一个工具 */
    public static final ToolChoice REQUIRED = new ToolChoice("required", null);

    private final String strategy;
    private final String functionName;

    private ToolChoice(String strategy, String functionName) {
        this.strategy = strategy;
        this.functionName = functionName;
    }

    /**
     * 强制调用指定名称的工具。
     *
     * @param name 工具函数名称
     * @return ToolChoice 实例
     */
    public static ToolChoice function(String name) {
        return new ToolChoice("function", name);
    }

    /**
     * 序列化为 OpenAI 兼容格式。
     * <p>简单策略（auto/none/required）→ 字符串；指定函数 → {"type":"function","function":{"name":"..."}}</p>
     */
    Object toWireFormat() {
        if (functionName != null) {
            Map<String, Object> funcObj = new LinkedHashMap<>();
            funcObj.put("type", "function");
            Map<String, String> nameObj = new LinkedHashMap<>();
            nameObj.put("name", functionName);
            funcObj.put("function", nameObj);
            return funcObj;
        }
        return strategy;
    }

    @Override
    public String toString() {
        return functionName != null
                ? "ToolChoice{function=" + functionName + "}"
                : "ToolChoice{" + strategy + "}";
    }

    /**
     * Jackson 序列化器。
     */
    static class ToolChoiceSerializer extends JsonSerializer<ToolChoice> {
        @Override
        public void serialize(ToolChoice value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            Object wire = value.toWireFormat();
            if (wire instanceof String s) {
                gen.writeString(s);
            } else {
                gen.writeObject(wire);
            }
        }
    }

    /**
     * Jackson 反序列化器：兼容字符串（auto/none/required）和对象（{"type":"function","function":{"name":"x"}}）。
     */
    static class ToolChoiceDeserializer extends JsonDeserializer<ToolChoice> {
        @Override
        public ToolChoice deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.currentToken().isScalarValue()) {
                String val = p.getText();
                return switch (val) {
                    case "auto" -> AUTO;
                    case "none" -> NONE;
                    case "required" -> REQUIRED;
                    default -> AUTO; // 容错
                };
            }
            // 对象格式：{"type":"function","function":{"name":"x"}}
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = p.readValueAs(Map.class);
            if ("function".equals(obj.get("type"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> func = (Map<String, Object>) obj.get("function");
                if (func != null && func.get("name") != null) {
                    return ToolChoice.function(func.get("name").toString());
                }
            }
            return AUTO; // 容错
        }
    }
}
