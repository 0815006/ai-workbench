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
 * 写入文件工具（{@code writeFile}）。
 * <p>创建新文件或覆盖写入完整内容（适用于全量生成代码/配置），
 * 自动创建父目录，强制沙箱路径校验。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>路径穿越防护：{@link PathSafety#resolveSafePath} 强制限定在 baseDir 内</li>
 *   <li>写入内容大小上限：防止单次写入超大内容</li>
 * </ul>
 */
@Slf4j
public class WriteFileTool implements AgentTool<WriteFileTool.Request, String> {

    private final BaseToolConfig config;

    public WriteFileTool(BaseToolConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "writeFile";
    }

    @Override
    public String description() {
        return "创建新文件或覆盖写入完整内容（适用于全量生成代码/配置）。"
                + "自动创建父目录，路径必须位于沙箱根目录内。";
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
        if (request.content().length() > config.getMaxOutputChars() * 10) {
            throw new IOException(String.format(
                    "写入内容过大（%d 字符），已拒绝。", request.content().length()));
        }

        // 自动创建父目录
        Path parent = filePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Files.writeString(filePath, request.content(), StandardCharsets.UTF_8);
        long size = Files.size(filePath);

        log.info("writeFile: {} ({} bytes)", filePath, size);
        return String.format("写入成功: %s\n文件大小: %d bytes", filePath, size);
    }

    /**
     * 写入文件请求参数。
     *
     * @param filePath 文件路径（相对沙箱根目录或绝对路径）
     * @param content  完整文件内容
     */
    public record Request(
            @ToolParam(description = "文件路径（相对沙箱根目录或绝对路径）", required = true)
            String filePath,

            @ToolParam(description = "完整文件内容", required = true)
            String content
    ) {
    }
}