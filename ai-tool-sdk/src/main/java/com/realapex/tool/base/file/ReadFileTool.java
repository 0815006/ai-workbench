package com.realapex.tool.base.file;

import com.realapex.tool.annotation.ToolParam;
import com.realapex.tool.base.BaseToolConfig;
import com.realapex.tool.base.OutputTruncator;
import com.realapex.tool.base.PathSafety;
import com.realapex.tool.contract.AgentTool;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 读取文件工具（{@code readFile}）。
 * <p>读取文本/代码文件内容，支持指定编码、按行区间分段读取（防大文件爆 Token），
 * 并强制沙箱路径校验与输出截断。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>路径穿越防护：{@link PathSafety#resolveSafePath} 强制限定在 baseDir 内</li>
 *   <li>大文件防护：超过 {@code maxFileSizeBytes} 拒绝读取</li>
 *   <li>Token 爆炸防护：返回结果经 {@link OutputTruncator#truncate} 截断</li>
 * </ul>
 */
@Slf4j
public class ReadFileTool implements AgentTool<ReadFileTool.Request, String> {

    private final BaseToolConfig config;

    public ReadFileTool(BaseToolConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "readFile";
    }

    @Override
    public String description() {
        return "读取文本/代码文件内容。支持指定编码、按行区间分段读取（防大文件爆 Token）。"
                + "路径必须位于沙箱根目录内，超过大小上限的文件将被拒绝。";
    }

    @Override
    public Class<Request> requestClass() {
        return Request.class;
    }

    @Override
    public String execute(Request request) throws Exception {
        Path baseDir = config.effectiveBaseDir();
        Path filePath = PathSafety.resolveSafePath(baseDir, request.filePath());

        if (!Files.exists(filePath)) {
            throw new IOException("文件不存在: " + filePath);
        }
        if (Files.isDirectory(filePath)) {
            throw new IOException("目标是一个目录，请使用 readDir: " + filePath);
        }

        long size = Files.size(filePath);
        if (size > config.getMaxFileSizeBytes()) {
            throw new IOException(String.format(
                    "文件过大（%d bytes > 上限 %d bytes），拒绝读取。请使用 searchContent 定位片段。",
                    size, config.getMaxFileSizeBytes()));
        }

        Charset charset = request.encoding() == null || request.encoding().isBlank()
                ? StandardCharsets.UTF_8
                : Charset.forName(request.encoding());

        List<String> lines = Files.readAllLines(filePath, charset);
        int totalLines = lines.size();

        // 行区间过滤
        int start = request.startLine() != null ? Math.max(1, request.startLine()) : 1;
        int end = request.endLine() != null ? Math.min(totalLines, request.endLine()) : totalLines;
        if (start > end) {
            throw new IllegalArgumentException(
                    String.format("startLine(%d) 不能大于 endLine(%d)", start, end));
        }

        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            sb.append(lines.get(i - 1)).append('\n');
        }

        String content = sb.toString();
        String truncated = OutputTruncator.truncate(content, config.getMaxOutputChars());

        log.debug("readFile: {} ({} lines, {} bytes)", filePath, totalLines, size);
        return String.format("文件: %s\n总行数: %d\n读取区间: [%d, %d]\n%s",
                filePath, totalLines, start, end, truncated);
    }

    /**
     * 读取文件请求参数。
     *
     * @param filePath  文件路径（相对沙箱根目录或绝对路径）
     * @param encoding  文件编码（可选，默认 UTF-8）
     * @param startLine 起始行号（可选，从 1 开始）
     * @param endLine   结束行号（可选，含该行）
     */
    public record Request(
            @ToolParam(description = "文件路径（相对沙箱根目录或绝对路径）", required = true)
            String filePath,

            @ToolParam(description = "文件编码（可选，默认 UTF-8）", required = false)
            String encoding,

            @ToolParam(description = "起始行号（可选，从 1 开始）", required = false)
            Integer startLine,

            @ToolParam(description = "结束行号（可选，含该行）", required = false)
            Integer endLine
    ) {
    }
}