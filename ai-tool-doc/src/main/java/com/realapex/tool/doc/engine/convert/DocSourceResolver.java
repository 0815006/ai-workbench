package com.realapex.tool.doc.engine.convert;

import com.realapex.tool.base.PathSafety;
import com.realapex.tool.doc.config.DocToolConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;

/**
 * 文档来源解析器——将路径 / URL / Base64 三种来源归一化为沙箱内本地 Path。
 * <p>统一入口，屏蔽来源差异；URL 仅允许 http/https，下载与解码均受
 * {@code maxDocSizeBytes} 大小卡口约束。</p>
 */
public final class DocSourceResolver {

    private DocSourceResolver() {
    }

    /**
     * 解析本地文件路径（复用 {@link PathSafety} 做沙箱路径穿越防护）。
     *
     * @param source  用户传入的路径（相对沙箱根目录或绝对路径）
     * @param baseDir 沙箱根目录
     * @return 规范化后的安全绝对路径
     */
    public static Path resolvePath(String source, Path baseDir) {
        return PathSafety.resolveSafePath(baseDir, source);
    }

    /**
     * 统一解析入口：路径 / URL / Base64 三态归一化为沙箱内本地 Path。
     * <p>按前缀自动路由：{@code http(s)://} → 下载；{@code base64:} → 解码；其余 → 本地路径。</p>
     *
     * @param source 文档来源（本地路径 / http(s) URL / base64: 前缀字符串）
     * @param config 工具包配置
     * @return 归一化后的本地文件路径
     * @throws IOException 下载/解码失败时抛出
     */
    public static Path resolve(String source, DocToolConfig config) throws IOException {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("文档来源不能为空");
        }
        String trimmed = source.trim();
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return resolveUrl(trimmed, config);
        }
        if (lower.startsWith("base64:")) {
            return resolveBase64(trimmed.substring("base64:".length()), config);
        }
        return resolvePath(trimmed, config.effectiveBaseDir());
    }

    /**
     * 下载 URL 到临时目录（仅允许 http/https，受大小卡口约束）。
     *
     * @param url    http(s) 地址
     * @param config 工具包配置
     * @return 下载后的本地临时文件路径
     * @throws IOException 下载失败或超限时抛出
     */
    public static Path resolveUrl(String url, DocToolConfig config) throws IOException {
        URI uri = URI.create(url);
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new SecurityException("仅允许 http/https URL: " + url);
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofMillis(config.getTimeoutMs()))
                .GET()
                .build();

        HttpResponse<byte[]> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("URL 下载被中断: " + url, e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("URL 下载失败，HTTP " + response.statusCode() + ": " + url);
        }

        byte[] body = response.body();
        if (body.length > config.getMaxDocSizeBytes()) {
            throw new IOException("URL 下载内容超过大小上限: " + body.length + " bytes");
        }

        Path tempDir = config.effectiveTempDir();
        Files.createDirectories(tempDir);
        String fileName = fileNameFromUrl(uri);
        Path target = tempDir.resolve(fileName);
        Files.write(target, body);
        return target;
    }

    /**
     * 解码 Base64 到临时目录（受大小卡口约束）。
     *
     * @param base64 Base64 字符串
     * @param config 工具包配置
     * @return 解码后的本地临时文件路径
     * @throws IOException 解码失败或超限时抛出
     */
    public static Path resolveBase64(String base64, DocToolConfig config) throws IOException {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new IOException("Base64 解码失败: " + e.getMessage(), e);
        }
        if (bytes.length > config.getMaxDocSizeBytes()) {
            throw new IOException("Base64 解码内容超过大小上限: " + bytes.length + " bytes");
        }

        Path tempDir = config.effectiveTempDir();
        Files.createDirectories(tempDir);
        Path target = tempDir.resolve("base64-doc-" + System.currentTimeMillis() + ".bin");
        Files.write(target, bytes);
        return target;
    }

    /**
     * 从 URL 提取文件名（无文件名时回退时间戳命名）。
     *
     * @param uri URL
     * @return 文件名
     */
    private static String fileNameFromUrl(URI uri) {
        String path = uri.getPath();
        if (path != null && !path.isBlank() && !path.endsWith("/")) {
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (!name.isBlank()) {
                return name;
            }
        }
        return "url-doc-" + System.currentTimeMillis() + ".bin";
    }
}