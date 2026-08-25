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
 * 获取目录树工具（{@code getTree}）。
 * <p>以树状图文本输出项目目录结构（类似 {@code tree} 命令），
 * 支持忽略模式过滤（如 {@code node_modules}、{@code .git}、{@code target}）。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>路径穿越防护：{@link PathSafety#resolveSafePath} 强制限定在 baseDir 内</li>
 *   <li>Token 爆炸防护：结果经 {@link OutputTruncator#truncate} 截断</li>
 * </ul>
 */
@Slf4j
public class GetTreeTool implements AgentTool<GetTreeTool.Request, String> {

    private final BaseToolConfig config;

    public GetTreeTool(BaseToolConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "getTree";
    }

    @Override
    public String description() {
        return "以树状图输出项目目录结构（类似 tree 命令），支持忽略模式过滤"
                + "（如 node_modules、.git、target）。路径必须位于沙箱根目录内。";
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

        List<String> ignorePatterns = request.ignorePatterns() == null
                ? List.of()
                : request.ignorePatterns();

        List<String> lines = new ArrayList<>();
        lines.add(dirPath.toString());
        buildTree(dirPath, "", ignorePatterns, lines);

        String result = String.join("\n", lines);
        log.debug("getTree: {} ({} lines)", dirPath, lines.size());
        return OutputTruncator.truncate(result, config.getMaxOutputChars());
    }

    private void buildTree(Path dir, String prefix, List<String> ignorePatterns, List<String> out)
            throws IOException {
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path child : stream) {
                String name = child.getFileName().toString();
                if (name.startsWith(".") || isIgnored(name, ignorePatterns)) {
                    continue;
                }
                children.add(child);
            }
        }
        children.sort((a, b) -> {
            boolean aDir = Files.isDirectory(a);
            boolean bDir = Files.isDirectory(b);
            if (aDir != bDir) {
                return aDir ? -1 : 1; // 目录优先
            }
            return a.getFileName().toString().compareTo(b.getFileName().toString());
        });

        for (int i = 0; i < children.size(); i++) {
            Path child = children.get(i);
            boolean last = (i == children.size() - 1);
            String connector = last ? "└── " : "├── ";
            out.add(prefix + connector + child.getFileName());

            if (Files.isDirectory(child)) {
                buildTree(child, prefix + (last ? "    " : "│   "), ignorePatterns, out);
            }
        }
    }

    private boolean isIgnored(String name, List<String> ignorePatterns) {
        for (String pattern : ignorePatterns) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            String p = pattern.trim();
            if (p.startsWith("**/")) {
                p = p.substring(3);
            }
            if (name.equals(p) || name.matches(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取目录树请求参数。
     *
     * @param dirPath        目录路径（相对沙箱根目录或绝对路径）
     * @param ignorePatterns 忽略模式列表（可选，如 [node_modules, target, .git]）
     */
    public record Request(
            @ToolParam(description = "目录路径（相对沙箱根目录或绝对路径）", required = true)
            String dirPath,

            @ToolParam(description = "忽略模式列表（可选，如 [node_modules, target, .git]）", required = false)
            List<String> ignorePatterns
    ) {
    }
}