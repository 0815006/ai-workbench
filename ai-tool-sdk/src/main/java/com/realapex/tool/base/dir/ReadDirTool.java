package com.realapex.tool.base.dir;

import com.realapex.tool.annotation.ToolParam;
import com.realapex.tool.base.BaseToolConfig;
import com.realapex.tool.base.OutputTruncator;
import com.realapex.tool.base.PathSafety;
import com.realapex.tool.contract.AgentTool;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 读取目录工具（{@code readDir} / {@code listDir}）。
 * <p>列出指定目录下的文件与子目录，支持递归深度控制与隐藏文件过滤，
 * 帮助 Agent 快速建立对项目结构的整体认知。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>路径穿越防护：{@link PathSafety#resolveSafePath} 强制限定在 baseDir 内</li>
 *   <li>Token 爆炸防护：结果经 {@link OutputTruncator#truncate} 截断</li>
 * </ul>
 */
@Slf4j
public class ReadDirTool implements AgentTool<ReadDirTool.Request, String> {

    private final BaseToolConfig config;

    public ReadDirTool(BaseToolConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "readDir";
    }

    @Override
    public String description() {
        return "列出指定目录下的文件与子目录（可配置递归深度、过滤隐藏文件），"
                + "帮助快速了解项目结构。路径必须位于沙箱根目录内。";
    }

    @Override
    public Class<Request> requestClass() {
        return Request.class;
    }

    @Override
    public String execute(Request request) throws Exception {
        Path baseDir = config.effectiveBaseDir();
        Path dirPath = PathSafety.resolveSafePath(baseDir, request.dirPath());

        if (!Files.exists(dirPath)) {
            throw new IOException("目录不存在: " + dirPath);
        }
        if (!Files.isDirectory(dirPath)) {
            throw new IOException("目标不是目录: " + dirPath);
        }

        int maxDepth = request.maxDepth() != null ? request.maxDepth() : 1;
        boolean showHidden = Boolean.TRUE.equals(request.showHidden());

        List<String> entries = new ArrayList<>();
        walk(dirPath, 0, maxDepth, showHidden, entries);

        String result = String.join("\n", entries);
        log.debug("readDir: {} ({} entries)", dirPath, entries.size());
        return OutputTruncator.truncate(result, config.getMaxOutputChars());
    }

    private void walk(Path dir, int depth, int maxDepth, boolean showHidden, List<String> out)
            throws IOException {
        if (depth > maxDepth) {
            return;
        }
        String indent = "  ".repeat(depth);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                if (!showHidden && name.startsWith(".")) {
                    continue;
                }
                boolean isDir = Files.isDirectory(child);
                out.add(indent + (isDir ? "[DIR]  " : "[FILE] ") + name);
                if (isDir && depth < maxDepth) {
                    walk(child, depth + 1, maxDepth, showHidden, out);
                }
            }
        }
    }

    /**
     * 读取目录请求参数。
     *
     * @param dirPath    目录路径（相对沙箱根目录或绝对路径）
     * @param recursive  是否递归（可选，默认 false）
     * @param maxDepth   最大递归深度（可选，默认 1）
     * @param showHidden 是否显示隐藏文件（可选，默认 false）
     */
    public record Request(
            @ToolParam(description = "目录路径（相对沙箱根目录或绝对路径）", required = true)
            String dirPath,

            @ToolParam(description = "是否递归列出（可选，默认 false）", required = false)
            Boolean recursive,

            @ToolParam(description = "最大递归深度（可选，默认 1）", required = false)
            Integer maxDepth,

            @ToolParam(description = "是否显示隐藏文件（可选，默认 false）", required = false)
            Boolean showHidden
    ) {
    }
}