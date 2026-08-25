package com.realapex.tool.base.file;

import com.realapex.tool.annotation.ToolParam;
import com.realapex.tool.base.BaseToolConfig;
import com.realapex.tool.base.PathSafety;
import com.realapex.tool.contract.AgentTool;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 写 Markdown 工具（{@code writeMarkdown}）。
 * <p>结构化写入 Markdown 文档，支持追加模式（{@code appendMode}），
 * 适用于生成文档、报告等场景。自动创建父目录并强制沙箱路径校验。</p>
 *
 * <h3>追加模式</h3>
 * <ul>
 *   <li>{@code appendMode=false}（默认）：覆盖写入完整内容</li>
 *   <li>{@code appendMode=true}：在文件末尾追加内容（文件不存在时等同创建）</li>
 * </ul>
 */
@Slf4j
public class WriteMarkdownTool implements AgentTool<WriteMarkdownTool.Request, String> {

    private final BaseToolConfig config;

    public WriteMarkdownTool(BaseToolConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "writeMarkdown";
    }

    @Override
    public String description() {
        return "结构化写入 Markdown 文档，支持追加模式（appendMode=true 时在文件末尾追加）。"
                + "适用于生成文档、报告，路径必须位于沙箱根目录内。";
    }

    @Override
    public Class<Request> requestClass() {
        return Request.class;
    }

    @Override
    public String execute(Request request) throws Exception {
        Path baseDir = config.effectiveBaseDir();
        Path filePath = PathSafety.resolveSafePath(baseDir, request.filePath());

        if (request.content() == null) {
            throw new IllegalArgumentException("content 不能为空");
        }

        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        boolean append = Boolean.TRUE.equals(request.appendMode());
        String normalized = request.content().trim() + "\n";

        if (append && Files.exists(filePath)) {
            Files.writeString(filePath, normalized, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);
        } else {
            Files.writeString(filePath, normalized, StandardCharsets.UTF_8);
        }

        long size = Files.size(filePath);
        log.info("writeMarkdown: {} (append={}, {} bytes)", filePath, append, size);
        return String.format("Markdown 写入%s成功: %s\n文件大小: %d bytes",
                append ? "（追加）" : "", filePath, size);
    }

    /**
     * 写 Markdown 请求参数。
     *
     * @param filePath   文件路径（相对沙箱根目录或绝对路径）
     * @param content    Markdown 内容
     * @param appendMode 是否追加模式（可选，默认 false 覆盖写入）
     */
    public record Request(
            @ToolParam(description = "文件路径（相对沙箱根目录或绝对路径）", required = true)
            String filePath,

            @ToolParam(description = "Markdown 内容", required = true)
            String content,

            @ToolParam(description = "是否追加模式（可选，默认 false 覆盖写入）", required = false)
            Boolean appendMode
    ) {
    }
}