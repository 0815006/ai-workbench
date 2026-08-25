package com.realapex.tool.base.search;

import com.realapex.tool.annotation.ToolParam;
import com.realapex.tool.base.BaseToolConfig;
import com.realapex.tool.base.OutputTruncator;
import com.realapex.tool.base.PathSafety;
import com.realapex.tool.contract.AgentTool;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 文本/代码内容搜索工具（{@code searchContent} / {@code grep}）。
 * <p>基于关键词或正则表达式全文检索匹配的代码行（对标 {@code ripgrep}），
 * 返回包含文件名、行号、匹配文本的数组。支持文件类型过滤。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>路径穿越防护：搜索根目录强制限定在 baseDir 内</li>
 *   <li>Token 爆炸防护：结果经 {@link OutputTruncator#truncate} 截断</li>
 * </ul>
 */
@Slf4j
public class SearchContentTool implements AgentTool<SearchContentTool.Request, String> {

    private final BaseToolConfig config;

    public SearchContentTool(BaseToolConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "searchContent";
    }

    @Override
    public String description() {
        return "基于关键词或正则表达式全文检索匹配的代码行（对标 ripgrep），"
                + "返回 [文件名:行号] 匹配文本。支持 filePattern 过滤文件类型。"
                + "搜索范围限定在沙箱根目录内。";
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
        if (request.query() == null || request.query().isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }

        boolean isRegex = Boolean.TRUE.equals(request.isRegex());
        Pattern pattern = isRegex
                ? Pattern.compile(request.query())
                : Pattern.compile(Pattern.quote(request.query()));

        PathMatcher fileMatcher = request.filePattern() == null || request.filePattern().isBlank()
                ? null
                : FileSystems.getDefault().getPathMatcher("glob:" + request.filePattern());

        List<String> hits = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(searchRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> fileMatcher == null || fileMatcher.matches(searchRoot.relativize(p)))
                    .forEach(p -> searchInFile(p, searchRoot, pattern, hits));
        }

        String result = hits.isEmpty()
                ? "未找到匹配内容: " + request.query()
                : String.format("匹配 %d 处（query: %s）:%n%s",
                        hits.size(), request.query(), String.join("\n", hits));

        log.debug("searchContent: query={}, regex={}, hits={}", request.query(), isRegex, hits.size());
        return OutputTruncator.truncate(result, config.getMaxOutputChars());
    }

    private void searchInFile(Path file, Path root, Pattern pattern, List<String> hits) {
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String relative = root.relativize(file).toString();
            for (int i = 0; i < lines.size(); i++) {
                if (pattern.matcher(lines.get(i)).find()) {
                    hits.add(relative + ":" + (i + 1) + ": " + lines.get(i).trim());
                }
            }
        } catch (IOException e) {
            log.debug("跳过无法读取的文件: {} ({})", file, e.getMessage());
        }
    }

    /**
     * 内容搜索请求参数。
     *
     * @param query       搜索关键词或正则表达式
     * @param isRegex     是否按正则匹配（可选，默认 false 按字面量）
     * @param filePattern 文件类型过滤（可选，如 *.java、*.md）
     * @param baseDir     搜索根目录（可选，默认沙箱根目录）
     */
    public record Request(
            @ToolParam(description = "搜索关键词或正则表达式", required = true)
            String query,

            @ToolParam(description = "是否按正则匹配（可选，默认 false 按字面量）", required = false)
            Boolean isRegex,

            @ToolParam(description = "文件类型过滤（可选，如 *.java、*.md）", required = false)
            String filePattern,

            @ToolParam(description = "搜索根目录（可选，默认沙箱根目录）", required = false)
            String baseDir
    ) {
    }
}