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
 * 局部增量更新工具（{@code editFile} / {@code applyPatch}）。
 * <p>精准替换文件中的某段代码/文本，避免小修改却重写整个大文件。
 * 基于 {@code oldString → newString} 精确匹配替换，并返回匹配行号。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>路径穿越防护：{@link PathSafety#resolveSafePath} 强制限定在 baseDir 内</li>
 *   <li>精确匹配：oldString 必须唯一匹配，防止误替换</li>
 * </ul>
 */
@Slf4j
public class EditFileTool implements AgentTool<EditFileTool.Request, String> {

    private final BaseToolConfig config;

    public EditFileTool(BaseToolConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "editFile";
    }

    @Override
    public String description() {
        return "精准替换文件中的某段代码/文本（局部增量更新，避免重写整个大文件）。"
                + "oldString 必须唯一匹配，替换后返回匹配行号。";
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
        if (request.oldString() == null || request.oldString().isBlank()) {
            throw new IllegalArgumentException("oldString 不能为空");
        }
        if (request.newString() == null) {
            throw new IllegalArgumentException("newString 不能为空");
        }

        String content = Files.readString(filePath, StandardCharsets.UTF_8);

        int index = content.indexOf(request.oldString());
        if (index < 0) {
            throw new IOException("未找到匹配内容 oldString，请检查内容是否一致（含缩进/换行）。");
        }
        // 唯一性校验：再次查找确认只有一处匹配
        int secondIndex = content.indexOf(request.oldString(), index + 1);
        if (secondIndex >= 0) {
            throw new IOException("oldString 在文件中出现多次，请提供更长的上下文以确保唯一匹配。");
        }

        // 计算匹配起始行号
        int lineNumber = 1;
        for (int i = 0; i < index; i++) {
            if (content.charAt(i) == '\n') {
                lineNumber++;
            }
        }

        String updated = content.substring(0, index)
                + request.newString()
                + content.substring(index + request.oldString().length());

        Files.writeString(filePath, updated, StandardCharsets.UTF_8);

        log.info("editFile: {} (line {})", filePath, lineNumber);
        return String.format("替换成功: %s\n匹配起始行号: %d\n替换字符数: %d → %d",
                filePath, lineNumber, request.oldString().length(), request.newString().length());
    }

    /**
     * 局部更新请求参数。
     *
     * @param filePath  文件路径（相对沙箱根目录或绝对路径）
     * @param oldString 待替换的原文（必须唯一匹配）
     * @param newString 替换后的新内容
     */
    public record Request(
            @ToolParam(description = "文件路径（相对沙箱根目录或绝对路径）", required = true)
            String filePath,

            @ToolParam(description = "待替换的原文（必须唯一匹配，含缩进/换行）", required = true)
            String oldString,

            @ToolParam(description = "替换后的新内容", required = true)
            String newString
    ) {
    }
}