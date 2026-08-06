package com.realapex.client.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realapex.client.exception.ParseException;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 容错解析引擎。
 * <p>大模型经常在 JSON 外围包裹 Markdown 代码块标记（```json ... ```），
 * 此工具类自动清洗并提取纯 JSON 内容后再反序列化。</p>
 */
@Slf4j
public class JsonRepairParser {

    private final ObjectMapper objectMapper;

    public JsonRepairParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从大模型原始响应中提取 JSON 并反序列化为目标类型。
     * <p>自动处理以下情况：
     * <ul>
     *   <li>Markdown 代码块包裹：```json { ... } ```</li>
     *   <li>无标记的纯 JSON</li>
     *   <li>前后有额外文字说明</li>
     * </ul>
     * </p>
     *
     * @param rawText     大模型原始响应文本
     * @param targetClass 目标 Java 类型
     * @param <T>         目标类型
     * @return 反序列化后的对象
     * @throws ParseException JSON 提取或解析失败时抛出
     */
    public <T> T parse(String rawText, Class<T> targetClass) {
        String json = extractJson(rawText);
        try {
            return objectMapper.readValue(json, targetClass);
        } catch (Exception e) {
            throw new ParseException(
                    "JSON 反序列化为 " + targetClass.getSimpleName() + " 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从文本中提取 JSON 字符串。
     * <p>优先匹配第一个 '{' 到最后一个 '}' 之间的内容。</p>
     *
     * @param text 原始文本
     * @return 清洗后的 JSON 字符串
     * @throws ParseException 未找到有效 JSON 时抛出
     */
    String extractJson(String text) {
        if (text == null || text.isBlank()) {
            throw new ParseException("响应文本为空，无法提取 JSON");
        }

        // 去除 Markdown 代码块标记
        String cleaned = text
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        // 定位第一个 { 到最后一个 }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start == -1 || end == -1 || start >= end) {
            // 尝试数组格式 [ ... ]
            start = cleaned.indexOf('[');
            end = cleaned.lastIndexOf(']');
        }
        if (start == -1 || end == -1 || start >= end) {
            throw new ParseException("响应中未找到有效 JSON 结构，原始内容前100字符: "
                    + text.substring(0, Math.min(100, text.length())));
        }

        return cleaned.substring(start, end + 1);
    }
}
