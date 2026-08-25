package com.realapex.tool.base.search;

import com.realapex.tool.annotation.ToolParam;
import com.realapex.tool.base.BaseToolConfig;
import com.realapex.tool.base.OutputTruncator;
import com.realapex.tool.base.PathSafety;
import com.realapex.tool.contract.AgentTool;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 文件名通配搜索工具（{@code searchFiles} / {@code glob}）。
 * <p>基于 Glob 表达式按文件名搜索（如 {@code **\/*.java}、{@code src\/**\/*.ts}），
 * 返回匹配文件的相对路径列表。相比全量读取目录，可极省 Token。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>路径穿越防护：搜索根目录强制限定在 baseDir 内</li>
 *   <li>Token 爆炸防护：结果经 {@link OutputTruncator#truncate} 截断</li>
 * </ul>
 */
@Slf4j
public class SearchFilesTool implements AgentTool<SearchFilesTool.Request, String> {

    private final BaseToolConfig config;

    public SearchFilesTool(BaseToolConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "searchFiles";
    }

    @Override
    public String description() {
        return "基于 Glob 表达式按文件名搜索（如 **/*.java、src/**/*.ts），"
                + "返回匹配文件的相对路径列表。搜索范围限定在沙箱根目录内。";
    }

    @Override
    public Class<Request> requestClass() {
        return Request.class;
    }

    @Override
    public String execute(Request request) throws Exception {
        Path baseDir = config.effectiveBaseDir();
        Path searchRoot = request.baseDir() == null || request.baseDir().isBlank()
                ? baseDir
                : PathSafety.resolveSafePath(baseDir, request.baseDir());

        if (!Files.exists(searchRoot)) {
            throw new IOException("搜索根目录不存在: " + searchRoot);
        }

        String pattern = request.pattern();
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pattern 不能为空");
        }

        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        List<String> matches = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(searchRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> matcher.matches(searchRoot.relativize(p)))
                    .forEach(p -> matches.add(searchRoot.relativize(p).toString()));
        }

        matches.sort(String::compareTo);
        String result = matches.isEmpty()
                ? "未找到匹配文件: " + pattern
                : String.format("匹配 %d 个文件（pattern: %s）:%n%s",
                        matches.size(), pattern, String.join("\n", matches));

        log.debug("searchFiles: pattern={}, root={}, hits={}", pattern, searchRoot, matches.size());
        return OutputTruncator.truncate(result, config.getMaxOutputChars());
    }

    /**
     * 文件名搜索请求参数。
     *
     * @param pattern Glob 表达式（如 {@code **\/*.java}、{@code src\/**\/*.ts}）
     * @param baseDir 搜索根目录（可选，默认沙箱根目录）
     */
    public record Request(
            @ToolParam(description = "Glob 表达式（如 **/*.java、src/**/*.ts）", required = true)
            String pattern,

            @ToolParam(description = "搜索根目录（可选，默认沙箱根目录）", required = false)
            String baseDir
    ) {
    }
}