package com.realapex.tool.base.system;

import com.realapex.tool.annotation.ToolParam;
import com.realapex.tool.base.BaseToolConfig;
import com.realapex.tool.base.OutputTruncator;
import com.realapex.tool.contract.AgentTool;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 网络 HTTP 请求工具（{@code fetchUrl} / {@code httpRequest}）。
 * <p>基于 JDK 原生 {@link HttpClient} 实现简单的 HTTP GET/POST 客户端，
 * 允许 Agent 抓取网页或调用外部 API。内置超时与输出截断。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>超时控制：默认 10 秒（{@code httpTimeoutMs}）</li>
 *   <li>Token 爆炸防护：响应体经 {@link OutputTruncator#truncate} 截断</li>
 *   <li>仅支持 http/https 协议，拒绝 file:// 等本地协议</li>
 * </ul>
 */
@Slf4j
public class FetchUrlTool implements AgentTool<FetchUrlTool.Request, String> {

    private final BaseToolConfig config;
    private final HttpClient httpClient;

    public FetchUrlTool(BaseToolConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getHttpTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public String name() {
        return "fetchUrl";
    }

    @Override
    public String description() {
        return "发起 HTTP GET/POST 请求抓取网页或调用外部 API。"
                + "仅支持 http/https 协议，内置超时（默认 10s）与响应截断。";
    }

    @Override
    public Class<Request> requestClass() {
        return Request.class;
    }

    @Override
    public String execute(Request request) throws Exception {
        if (request.url() == null || request.url().isBlank()) {
            throw new IllegalArgumentException("url 不能为空");
        }

        URI uri = URI.create(request.url());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new SecurityException("仅允许 http/https 协议，已拒绝: " + request.url());
        }

        String method = request.method() == null || request.method().isBlank()
                ? "GET"
                : request.method().toUpperCase();

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(config.getHttpTimeoutMs()));

        if (request.headers() != null) {
            request.headers().forEach(builder::header);
        }

        if ("GET".equals(method)) {
            builder.GET();
        } else if ("POST".equals(method)) {
            String body = request.body() == null ? "" : request.body();
            builder.POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            throw new IllegalArgumentException("仅支持 GET/POST，已拒绝: " + method);
        }

        HttpResponse<String> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());

        String result = String.format("HTTP %d %s%n--- response body ---%n%s",
                response.statusCode(), response.uri(), response.body());
        log.info("fetchUrl: {} {} → {}", method, request.url(), response.statusCode());
        return OutputTruncator.truncate(result, config.getMaxOutputChars());
    }

    /**
     * HTTP 请求参数。
     *
     * @param url     目标 URL（仅 http/https）
     * @param method  请求方法（可选，默认 GET，支持 GET/POST）
     * @param headers 请求头（可选）
     * @param body    请求体（可选，POST 时使用）
     */
    public record Request(
            @ToolParam(description = "目标 URL（仅支持 http/https）", required = true)
            String url,

            @ToolParam(description = "请求方法（可选，默认 GET，支持 GET/POST）", required = false)
            String method,

            @ToolParam(description = "请求头（可选）", required = false)
            Map<String, String> headers,

            @ToolParam(description = "请求体（可选，POST 时使用）", required = false)
            String body
    ) {
    }
}