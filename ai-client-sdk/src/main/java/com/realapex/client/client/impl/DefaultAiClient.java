package com.realapex.client.client.impl;

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
import com.realapex.client.skill.BaseSkill;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * {@link AiClient} 的默认实现。
 * <p>基于 JDK 21 原生 {@link HttpClient} + 虚拟线程，零第三方 HTTP 依赖。
 * 内部集成 Key 轮询、指数退避重试、SSE 流式解析、JSON 容错反序列化。</p>
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

    /**
     * 私有构造器，通过 {@link #create(AiConfig)} 工厂方法创建。
     */
    private DefaultAiClient(AiConfig config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
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

    // ==================== generateText ====================

    @Override
    public String generateText(AiRequest request) {
        AiRequest req = ensureModel(request);
        req.setStream(false);

        return retryHandler.executeWithRetry(() -> {
            try {
                HttpRequest httpReq = buildHttpRequest(req);
                HttpResponse<String> httpResp = httpClient.send(
                        httpReq, HttpResponse.BodyHandlers.ofString());
                handleHttpError(httpResp);
                AiResponse aiResp = objectMapper.readValue(
                        httpResp.body(), AiResponse.class);
                log.debug("generateText 完成, tokens: {}",
                        aiResp.getUsage() != null ? aiResp.getUsage().getTotalTokens() : "N/A");
                return aiResp.firstText();
            } catch (IOException e) {
                throw new AiClientException("网络请求失败: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiClientException("请求被中断", e);
            }
        });
    }

    // ==================== streamText ====================

    @Override
    public void streamText(AiRequest request, StreamListener listener) {
        AiRequest req = ensureModel(request);
        req.setStream(true);

        try {
            HttpRequest httpReq = buildHttpRequest(req);
            // JDK HttpClient 在虚拟线程中逐行消费 SSE 流
            httpClient.send(httpReq, HttpResponse.BodyHandlers.ofLines())
                    .body()
                    .forEach(line -> {
                        if (line.startsWith("data: ")) {
                            String data = line.substring(6);
                            if ("[DONE]".equals(data)) {
                                listener.onComplete();
                                return;
                            }
                            try {
                                AiResponse chunk = objectMapper.readValue(
                                        data, AiResponse.class);
                                if (chunk.getChoices() != null && !chunk.getChoices().isEmpty()) {
                                    var delta = chunk.getChoices().get(0).getDelta();
                                    if (delta != null && delta.getContent() != null) {
                                        listener.onChunk(delta.getContent());
                                    }
                                }
                            } catch (JsonProcessingException e) {
                                log.debug("SSE 行解析跳过: {}", line);
                            }
                        }
                    });
            // 如果流正常结束但未收到 [DONE]
            listener.onComplete();
        } catch (IOException e) {
            listener.onError(new AiClientException("SSE 流式请求失败: " + e.getMessage(), e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            listener.onError(new AiClientException("SSE 请求被中断", e));
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
     */
    private HttpRequest buildHttpRequest(AiRequest request) {
        String apiKey = keySelector.nextKey();
        String body;
        try {
            body = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new AiClientException("请求序列化失败", e);
        }

        return HttpRequest.newBuilder()
                .uri(URI.create(config.getBaseUrl() + CHAT_ENDPOINT))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(config.getTimeout())
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
