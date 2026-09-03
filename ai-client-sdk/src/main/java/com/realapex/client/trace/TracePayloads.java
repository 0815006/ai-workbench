package com.realapex.client.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realapex.client.model.AiRequest;
import com.realapex.client.model.AiResponse;
import com.realapex.client.model.ToolCall;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trace 载荷（JSONB）构建工具——将 {@link AiRequest}/{@link AiResponse} 转换为
 * 落盘用的 JSON 字符串，并支持长文本截断。
 * <p>注意：{@code ai-client-sdk} 不做任何业务序列化，此处直接将请求/响应对象
 * 映射为符合 PRD 的 {@code request_payload}/{@code response_payload} 结构，便于
 * 后续用 PostgreSQL JSONB 的 {@code @>} 等操作符检索。</p>
 */
public final class TracePayloads {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TracePayloads() {
    }

    /**
     * 构建请求载荷 JSON。
     * <pre>{ system_prompt, messages, tools, temperature, topP }</pre>
     *
     * @param request 统一请求对象
     * @return JSON 字符串，序列化失败时返回 {@code "{}"}
     */
    public static String buildRequestPayload(AiRequest request) {
        if (request == null) {
            return "{}";
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        // system prompt 单独提取，便于检索
        request.getMessages().stream()
                .filter(m -> "system".equals(m.getRole()))
                .findFirst()
                .ifPresent(m -> payload.put("system_prompt", m.getContent()));
        payload.put("messages", request.getMessages());
        payload.put("tools", request.getTools());
        payload.put("temperature", request.getTemperature());
        payload.put("top_p", request.getTopP());
        return write(payload);
    }

    /**
     * 构建响应载荷 JSON。
     * <pre>{ finish_reason, reasoning_content, assistant_message, tool_calls_executed }</pre>
     *
     * @param response 完整响应对象
     * @return JSON 字符串，序列化失败时返回 {@code "{}"}
     */
    public static String buildResponsePayload(AiResponse response) {
        if (response == null) {
            return "{}";
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("finish_reason", response.getFinishReason());
        payload.put("reasoning_content", response.getReasoningContent());

        Map<String, Object> assistant = new LinkedHashMap<>();
        assistant.put("role", "assistant");
        assistant.put("content", response.firstText());
        payload.put("assistant_message", assistant);

        List<ToolCall> calls = response.getToolCalls();
        if (calls != null && !calls.isEmpty()) {
            payload.put("tool_calls_executed", calls.stream().map(tc -> {
                Map<String, Object> call = new LinkedHashMap<>();
                call.put("tool_name", tc.getName());
                call.put("call_id", tc.getId());
                call.put("arguments", tc.getArguments());
                return call;
            }).toList());
        }
        return write(payload);
    }

    /**
     * 按配置上限截断 JSON 字符串（保留 JSON 可读性，简单截断并补尾）。
     *
     * @param json       JSON 字符串
     * @param maxLength  最大字符数，0 或负数表示不截断
     * @return 截断后的 JSON 字符串
     */
    public static String truncate(String json, long maxLength) {
        if (json == null) {
            return null;
        }
        if (maxLength <= 0 || json.length() <= maxLength) {
            return json;
        }
        return json.substring(0, (int) maxLength) + "... [TRUNCATED]";
    }

    private static String write(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}