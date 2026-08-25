package com.realapex.client.client.impl;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realapex.client.client.AiClient;
import com.realapex.client.client.StreamListener;
import com.realapex.client.config.AiConfig;
import com.realapex.client.exception.AiClientException;
import com.realapex.client.exception.AuthenticationException;
import com.realapex.client.executor.JsonRepairParser;
import com.realapex.client.executor.KeySelector;
import com.realapex.client.executor.RetryHandler;
import com.realapex.client.model.AiRequest;
import com.realapex.client.model.AiResponse;
import com.realapex.client.model.Choice;
import com.realapex.client.model.Message;
import com.realapex.client.model.ToolCall;
import com.realapex.client.model.Usage;
import com.realapex.client.provider.ModelProvider;
import com.realapex.client.provider.ModelProviderFactory;
import com.realapex.client.skill.BaseSkill;
import com.realapex.client.stream.StreamEvent;
import com.realapex.client.stream.StreamToolCallBuffer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * {@link AiClient} 的默认实现。
 * <p>基于 JDK 21 原生 {@link HttpClient} + 虚拟线程，零第三方 HTTP 依赖。
 * 内部集成 Key 轮询、指数退避重试（含抖动）、SSE 流式解析（含 ToolCall 增量拼接）、
 * JSON 容错反序列化、多厂商 ToolCall 解析容错。</p>
 *
 * <h3>线程安全</h3>
 * <p>本实现线程安全，可在多线程环境中复用同一个实例。</p>
 */
@Slf4j
public class DefaultAiClient implements AiClient {

    private static final String CHAT_ENDPOINT = "/chat/completions";

    private final AiConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final KeySelector keySelector;
    private final RetryHandler retryHandler;
    private final JsonRepairParser jsonRepairParser;
    private final ModelProvider modelProvider;

    /**
     * 私有构造器，通过 {@link #create(AiConfig)} 工厂方法创建。
     */
    private DefaultAiClient(AiConfig config) {
        this.config = config;
        this.objectMapper = buildObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getConnectTimeout())
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        this.keySelector = new KeySelector(
                config.getApiKeys(),
                config.getKeyBlacklistDuration().toSeconds());
        this.retryHandler = new RetryHandler(
                config.getMaxRetries(),
                config.getRetryBaseDelay());
        this.jsonRepairParser = new JsonRepairParser(objectMapper);
        this.modelProvider = ModelProviderFactory.create(config.getProvider());
    }

    /**
     * 构建带多厂商容错配置的 Jackson ObjectMapper。
     * <ul>
     *   <li>允许未转义控制字符（兼容 DeepSeek/Qwen 等厂商的 arguments 输出）</li>
     *   <li>允许单引号（部分厂商非标准 JSON）</li>
     *   <li>允许不带引号的字段名</li>
     * </ul>
     */
    private static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        return mapper;
    }

    /**
     * 创建 DefaultAiClient 实例。
     *
     * @param config SDK 配置，必须包含至少一个 API Key
     * @return DefaultAiClient 实例
     * @throws IllegalArgumentException config 校验不通过时抛出
     */
    public static DefaultAiClient create(AiConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("AiConfig must not be null");
        }
        if (config.getApiKeys() == null || config.getApiKeys().isEmpty()) {
            throw new IllegalArgumentException("至少需要配置一个 API Key");
        }
        return new DefaultAiClient(config);
    }

    // ==================== generate ====================

    @Override
    public AiResponse generate(AiRequest request) {
        AiRequest req = ensureModel(request);
        req.setStream(false);
        // 厂商协议适配（Ollama 等本地模型注入 Tool Prompt）
        AiRequest adaptedReq = modelProvider.adaptRequest(req);

        return retryHandler.executeWithRetry(() -> {
            try {
                HttpRequest httpReq = buildHttpRequest(adaptedReq);
                HttpResponse<String> httpResp = httpClient.send(
                        httpReq, HttpResponse.BodyHandlers.ofString());
                handleHttpError(httpResp);
                AiResponse aiResp = objectMapper.readValue(
                        httpResp.body(), AiResponse.class);
                // 厂商响应降级解析（非标准 JSON 时回退）
                AiResponse providerResp = modelProvider.parseResponse(httpResp.body());
                if (providerResp != null) {
                    aiResp = providerResp;
                }
                // 提取推理/思考链内容
                aiResp.setReasoningContent(modelProvider.extractReasoning(aiResp));
                log.debug("generate 完成, hasToolCalls={}, tokens={}, reasoning={}",
                        aiResp.hasToolCalls(),
                        aiResp.getUsage() != null ? aiResp.getUsage().getTotalTokens() : "N/A",
                        aiResp.getReasoningContent() != null ? "yes" : "no");
                return aiResp;
            } catch (IOException e) {
                throw new AiClientException("网络请求失败: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiClientException("请求被中断", e);
            }
        });
    }

    /**
     * 流式生成——SSE 流式读取 + 实时回调 + 完整 AiResponse。
     * <p>底层使用 {@link HttpResponse.BodyHandlers#ofLines()} 逐行解析 SSE 事件，
     * 通过 {@code listener} 实时推送增量文本，同时内部累积文本、工具调用、Token 用量，
     * 最终拼装为包含完整 tool_calls 的 {@link AiResponse} 返回。</p>
     */
    @Override
    public AiResponse generate(AiRequest request, StreamListener listener) {
        AiRequest req = ensureModel(request);
        req.setStream(true);
        // 厂商协议适配（Ollama 等本地模型注入 Tool Prompt）
        req = modelProvider.adaptRequest(req);
        return generateWithStreaming(req, listener);
    }

    /**
     * SSE 流式 LLM 调用——累积文本/工具调用/Token，同时通过 listener 实时推送。
     * <p>内部阻塞处理完整个 SSE 流后才返回拼装好的完整 AiResponse。</p>
     */
    private AiResponse generateWithStreaming(AiRequest req, StreamListener listener) {
        try {
            HttpRequest httpReq = buildHttpRequest(req);
            HttpResponse<java.util.stream.Stream<String>> httpResp = httpClient.send(
                    httpReq, HttpResponse.BodyHandlers.ofLines());

            int status = httpResp.statusCode();
            if (status != 200) {
                String errorBody = httpResp.body().reduce("", (a, b) -> a + b);
                handleHttpStreamError(status, errorBody, httpReq);
                throw new AiClientException("流式请求失败, HTTP " + status + ": " + errorBody);
            }

            StreamToolCallBuffer toolCallBuffer = new StreamToolCallBuffer();
            StringBuilder fullText = new StringBuilder();
            Usage[] usage = {null};
            String[] finishReason = {null};
            String[] responseId = {null};
            String[] model = {null};

            httpResp.body().forEach(line -> {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        toolCallBuffer.flush();
                        if (listener != null) listener.onComplete();
                        return;
                    }
                    try {
                        AiResponse chunk = objectMapper.readValue(data, AiResponse.class);

                        if (chunk.getId() != null) responseId[0] = chunk.getId();
                        if (chunk.getModel() != null) model[0] = chunk.getModel();
                        if (chunk.getUsage() != null) usage[0] = chunk.getUsage();

                        if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                            Choice choice = chunk.getChoices().get(0);
                            if (choice.getFinishReason() != null) {
                                finishReason[0] = choice.getFinishReason();
                            }

                            Choice.Delta delta = choice.getDelta();
                            if (delta != null) {
                                // 文本增量 → 累积 + 回调
                                if (delta.getContent() != null) {
                                    fullText.append(delta.getContent());
                                    if (listener != null) listener.onChunk(delta.getContent());
                                }
                                // 推理内容增量（DeepSeek-R1 / o1 思考链）
                                if (delta.getReasoningContent() != null) {
                                    fullText.append(delta.getReasoningContent());
                                    if (listener != null) listener.onReasoningChunk(delta.getReasoningContent());
                                }
                                // 工具调用增量 → Buffer 累积 + 回调
                                if (delta.getToolCalls() != null) {
                                    for (ToolCall tc : delta.getToolCalls()) {
                                        if (tc.getId() == null && tc.getFunction() == null) continue;
                                        if (listener != null) {
                                            listener.onToolCallChunk(tc.getId(), tc.getName(), tc.getArguments());
                                        }
                                        toolCallBuffer.accept(new StreamEvent.ToolCallChunk(
                                                tc.getIndex(), tc.getId(), tc.getName(), tc.getArguments()));
                                    }
                                }
                            }

                            // finish_reason 触发 flush
                            if ("tool_calls".equals(choice.getFinishReason())
                                    || "stop".equals(choice.getFinishReason())) {
                                toolCallBuffer.flush();
                            }
                        }
                    } catch (JsonProcessingException e) {
                        log.warn("SSE 行解析跳过: {} - 原因: {}", data, e.getMessage());
                    }
                }
            });

            // 流自然结束但未收到 [DONE]（兼容非标准 SSE）
            toolCallBuffer.flush();

            // === 拼装完整 AiResponse ===
            List<ToolCall> completedToolCalls = new ArrayList<>(toolCallBuffer.getCompletedCalls());

            Message respMsg = Message.builder()
                    .role("assistant")
                    .content(fullText.toString())
                    .toolCalls(completedToolCalls.isEmpty() ? null : completedToolCalls)
                    .build();

            Choice respChoice = Choice.builder()
                    .index(0)
                    .message(respMsg)
                    .finishReason(finishReason[0])
                    .build();

            AiResponse response = AiResponse.builder()
                    .id(responseId[0])
                    .model(model[0])
                    .choices(List.of(respChoice))
                    .usage(usage[0])
                    .build();
            // 提取推理/思考链内容（DeepSeek-R1 等）
            response.setReasoningContent(modelProvider.extractReasoning(response));

            log.debug("generate(streaming) 完成, text={} chars, toolCalls={}, tokens={}",
                    fullText.length(), completedToolCalls.size(),
                    usage[0] != null ? usage[0].getTotalTokens() : "N/A");

            return response;
        } catch (IOException e) {
            throw new AiClientException("流式请求失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiClientException("流式请求被中断", e);
        }
    }

    // ==================== generateText ====================

    @Override
    public String generateText(AiRequest request) {
        return generate(request).getText();
    }

    // ==================== streamText ====================

    @Override
    public void streamText(AiRequest request, StreamListener listener) {
        AiRequest req = ensureModel(request);
        req.setStream(true);

        try {
            HttpRequest httpReq = buildHttpRequest(req);
            HttpResponse<java.util.stream.Stream<String>> httpResp = httpClient.send(
                    httpReq, HttpResponse.BodyHandlers.ofLines());

            // 检查 HTTP 状态码，非 200 直接报错
            int status = httpResp.statusCode();
            if (status != 200) {
                String errorBody = httpResp.body().reduce("", (a, b) -> a + b);
                handleHttpStreamError(status, errorBody, httpReq);
                listener.onError(new AiClientException(
                        "SSE 流式请求失败, HTTP " + status + ": " + errorBody));
                return;
            }

            StreamToolCallBuffer toolCallBuffer = new StreamToolCallBuffer();
            boolean[] doneReceived = {false};
            int[] dataLineCount = {0};
            int[] chunkCount = {0};

            httpResp.body().forEach(line -> {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if ("[DONE]".equals(data)) {
                        dataLineCount[0]++;
                        doneReceived[0] = true;
                        // 流结束前 flush 所有未完成的 tool call
                        flushToolCallBuffer(toolCallBuffer, listener);
                        listener.onComplete();
                        return;
                    }
                    dataLineCount[0]++;
                    try {
                        AiResponse chunk = objectMapper.readValue(
                                data, AiResponse.class);

                        // 提取 usage（通常最后一帧携带）
                        if (chunk.getUsage() != null) {
                            listener.onUsage(chunk.getUsage());
                        }

                        if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                            Choice choice = chunk.getChoices().get(0);
                            Choice.Delta delta = choice.getDelta();
                            if (delta != null) {
                                // 文本增量
                                if (delta.getContent() != null) {
                                    chunkCount[0]++;
                                    listener.onChunk(delta.getContent());
                                }
                                // 推理内容增量（DeepSeek-R1 / o1 思考链）
                                if (delta.getReasoningContent() != null) {
                                    chunkCount[0]++;
                                    listener.onReasoningChunk(delta.getReasoningContent());
                                }
                                // 工具调用增量
                                if (delta.getToolCalls() != null) {
                                    for (ToolCall tc : delta.getToolCalls()) {
                                        String callId = tc.getId();
                                        String name = tc.getName();
                                        String argsDelta = tc.getArguments();

                                        // 处理 index-only 的 tool call chunk（部分厂商先发 index 帧）
                                        if (callId == null && tc.getFunction() == null) {
                                            continue;
                                        }

                                        // 通知业务层
                                        listener.onToolCallChunk(callId, name, argsDelta);

                                        // 内部 Buffer 累积
                                        toolCallBuffer.accept(
                                                new StreamEvent.ToolCallChunk(
                                                        tc.getIndex(), callId, name, argsDelta));
                                    }
                                }
                            }

                            // finish_reason 为 tool_calls 时 flush buffer
                            if ("tool_calls".equals(choice.getFinishReason())
                                    || "stop".equals(choice.getFinishReason())) {
                                flushToolCallBuffer(toolCallBuffer, listener);
                            }
                        }
                    } catch (JsonProcessingException e) {
                        log.warn("SSE 行解析跳过: {} - 原因: {}", line, e.getMessage());
                    }
                }
            });

            log.info("SSE 流结束: {} 行 data, {} 次 onChunk", dataLineCount[0], chunkCount[0]);
            // 如果流正常结束但未收到 [DONE]（兼容非标准 SSE 实现）
            if (!doneReceived[0]) {
                flushToolCallBuffer(toolCallBuffer, listener);
                listener.onComplete();
            }
        } catch (IOException e) {
            listener.onError(new AiClientException("SSE 流式请求失败: " + e.getMessage(), e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            listener.onError(new AiClientException("SSE 请求被中断", e));
        }
    }

    /**
     * Flush StreamToolCallBuffer 中累积的工具调用。
     */
    private void flushToolCallBuffer(StreamToolCallBuffer buffer, StreamListener listener) {
        if (buffer.hasPending()) {
            buffer.flush();
            List<ToolCall> completed = buffer.getCompletedCalls();
            log.debug("SSE ToolCallBuffer flush: {} 个完整 ToolCall", completed.size());
        }
    }

    /**
     * 处理流式请求的 HTTP 错误，触发认证失败/超限时的 Key 隔离。
     */
    private void handleHttpStreamError(int status, String errorBody, HttpRequest request) {
        if (status == 401 || status == 402) {
            String apiKey = request.headers()
                    .firstValue("Authorization").orElse("unknown")
                    .replace("Bearer ", "");
            keySelector.blacklist(apiKey);
            log.warn("API Key 已隔离 (HTTP {}): {}", status, errorBody);
        }
    }

    // ==================== generateObject ====================

    @Override
    public <T> T generateObject(AiRequest request, Class<T> responseType) {
        AiRequest req = ensureModel(request);
        // 自动注入 JSON 模式
        req.setResponseFormat(AiRequest.ResponseFormat.jsonObject());
        req.setStream(false);

        return retryHandler.executeWithRetry(() -> {
            try {
                HttpRequest httpReq = buildHttpRequest(req);
                HttpResponse<String> httpResp = httpClient.send(
                        httpReq, HttpResponse.BodyHandlers.ofString());
                handleHttpError(httpResp);
                String rawText = objectMapper.readValue(
                        httpResp.body(), AiResponse.class).firstText();
                T result = jsonRepairParser.parse(rawText, responseType);
                log.debug("generateObject 完成, type={}", responseType.getSimpleName());
                return result;
            } catch (IOException e) {
                throw new AiClientException("网络请求失败: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiClientException("请求被中断", e);
            }
        });
    }

    // ==================== executeSkill ====================

    @Override
    public <I, O> O executeSkill(BaseSkill<I, O> skill, I input) {
        log.debug("执行 Skill: {}", skill.getClass().getSimpleName());
        return skill.execute(this, input);
    }

    // ==================== 内部方法 ====================

    /**
     * 构建 HTTP 请求，自动注入当前轮询到的 API Key。
     * <p>超时策略：SSE 流式请求使用 readTimeout，同步请求使用 timeout。</p>
     */
    private HttpRequest buildHttpRequest(AiRequest request) {
        String apiKey = keySelector.nextKey();
        String body;
        try {
            body = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new AiClientException("请求序列化失败", e);
        }

        // 流式请求使用 readTimeout（SSE 无数据包最大等待间隔）
        // 同步请求使用 timeout（完整请求总超时）
        Duration requestTimeout = Boolean.TRUE.equals(request.getStream())
                ? config.getReadTimeout()
                : config.getTimeout();

        return HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + CHAT_ENDPOINT))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    /**
     * 处理 HTTP 错误响应，抛出对应异常并触发 Key 隔离。
     */
    private void handleHttpError(HttpResponse<String> httpResp) {
        int status = httpResp.statusCode();
        if (status == 401 || status == 402) {
            // 认证/余额问题，隔离当前 Key
            String apiKey = httpResp.request().headers()
                    .firstValue("Authorization").orElse("unknown")
                    .replace("Bearer ", "");
            keySelector.blacklist(apiKey);
            throw new AuthenticationException(
                    "API Key 无效或余额不足 (HTTP " + status + "): " + httpResp.body());
        }
        if (status == 429) {
            throw new AiClientException("429 Rate Limit: " + httpResp.body());
        }
        if (status >= 500) {
            throw new AiClientException("HTTP " + status + " Server Error: " + httpResp.body());
        }
        if (status != 200) {
            throw new AiClientException("HTTP " + status + ": " + httpResp.body());
        }
    }

    /**
     * 若请求未指定 model，使用配置中的默认 model。
     */
    private AiRequest ensureModel(AiRequest request) {
        if (request.getModel() == null || request.getModel().isBlank()) {
            request.setModel(config.getModel());
        }
        return request;
    }
}
