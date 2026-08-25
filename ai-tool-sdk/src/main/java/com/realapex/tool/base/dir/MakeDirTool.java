package com.realapex.tool.base.dir;

import com.realapex.tool.annotation.ToolParam;
import com.realapex.tool.base.BaseToolConfig;
import com.realapex.tool.base.PathSafety;
import com.realapex.tool.contract.AgentTool;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 创建目录工具（{@code makeDir}）。
 * <p>递归创建文件夹路径（类似 {@code mkdir -p}），自动创建所有缺失的父目录。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>路径穿越防护：{@link PathSafety#resolveSafePath} 强制限定在 baseDir 内</li>
 * </ul>
 */
@Slf4j
public class MakeDirTool implements AgentTool<MakeDirTool.Request, String> {

    private final BaseToolConfig config;

    public MakeDirTool(BaseToolConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "makeDir";
    }

    @Override
    public String description() {
        return "递归创建文件夹路径（类似 mkdir -p），自动创建缺失的父目录。"
                + "路径必须位于沙箱根目录内。";
    }

    @Override
    public Class<Request> requestClass() {
        return Request.class;
    }

    @Override
    public String execute(Request request) throws Exception {
        Path baseDir = config.effectiveBaseDir();
        Path dirPath = PathSafety.resolveSafePath(baseDir, request.dirPath());

        if (Files.exists(dirPath)) {
            if (Files.isDirectory(dirPath)) {
                return "目录已存在: " + dirPath;
            }
            throw new IOException("目标路径已存在但不是目录: " + dirPath);
        }

        Files.createDirectories(dirPath);
        log.info("makeDir: {}", dirPath);
        return "目录创建成功: " + dirPath;
    }

    /**
     * 创建目录请求参数。
     *
     * @param dirPath 目录路径（相对沙箱根目录或绝对路径）
     */
    public record Request(
            @ToolParam(description = "目录路径（相对沙箱根目录或绝对路径）", required = true)
            String dirPath
    ) {
    }
}