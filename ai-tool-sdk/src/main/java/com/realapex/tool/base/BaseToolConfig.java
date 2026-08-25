package com.realapex.tool.base;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

/**
 * 基础工具库（{@code base}）统一配置。
 * <p>所有 {@code base} 基础工具共享此配置，用于注入沙箱根目录、输出截断上限、
 * 命令白名单与超时等安全参数。通过 {@link BaseToolFactory} 一键挂载工具组时传入。</p>
 *
 * <h3>安全默认值</h3>
 * <ul>
 *   <li>{@code maxFileSizeBytes}：单文件读取上限，默认 2MB，防止大文件爆 Token</li>
 *   <li>{@code maxOutputChars}：单个工具返回结果上限，默认 20,000 字符（PRD 要求）</li>
 *   <li>{@code commandTimeoutMs}：命令执行超时，默认 30 秒（PRD 要求）</li>
 *   <li>{@code httpTimeoutMs}：HTTP 请求超时，默认 10 秒</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * BaseToolConfig config = BaseToolConfig.builder()
 *         .baseDir(Path.of("/home/workspace/project-a"))
 *         .maxFileSizeBytes(2 * 1024 * 1024)
 *         .allowedCommands(List.of("npm test", "mvn compile", "git status"))
 *         .build();
 * }</pre>
 */
@Data
@Builder
public class BaseToolConfig {

    /** 默认单文件读取上限：2MB */
    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024L;

    /** 默认单工具返回结果字符上限：20,000（PRD 要求） */
    public static final int DEFAULT_MAX_OUTPUT_CHARS = 20_000;

    /** 默认命令执行超时：30 秒（PRD 要求） */
    public static final long DEFAULT_COMMAND_TIMEOUT_MS = 30_000;

    /** 默认 HTTP 请求超时：10 秒 */
    public static final long DEFAULT_HTTP_TIMEOUT_MS = 10_000;

    /** 默认环境变量白名单 */
    public static final List<String> DEFAULT_ENV_WHITELIST = List.of(
            "PATH", "JAVA_HOME", "HOME", "USER", "OS", "TMPDIR", "TEMP", "PWD");

    /**
     * 沙箱根目录（所有文件/目录/命令工具的工作根）。
     * <p>所有用户传入的路径都会基于此目录做 {@link PathSafety#resolveSafePath} 校验，
     * 防止 {@code ../../etc/passwd} 等路径穿越。</p>
     */
    private Path baseDir;

    /** 单文件读取大小上限（字节），默认 2MB */
    @Builder.Default
    private long maxFileSizeBytes = DEFAULT_MAX_FILE_SIZE_BYTES;

    /** 单个工具返回结果字符上限，默认 20,000 */
    @Builder.Default
    private int maxOutputChars = DEFAULT_MAX_OUTPUT_CHARS;

    /** 命令白名单前缀（如 "npm test"、"mvn compile"），为空表示仅靠黑名单过滤 */
    private List<String> allowedCommands;

    /** 命令执行超时（毫秒），默认 30 秒 */
    @Builder.Default
    private long commandTimeoutMs = DEFAULT_COMMAND_TIMEOUT_MS;

    /** HTTP 请求超时（毫秒），默认 10 秒 */
    @Builder.Default
    private long httpTimeoutMs = DEFAULT_HTTP_TIMEOUT_MS;

    /** 环境变量白名单（{@code getEnvInfo} 仅暴露这些变量） */
    @Builder.Default
    private List<String> envWhitelist = DEFAULT_ENV_WHITELIST;

    /**
     * 获取沙箱根目录，未配置时回退到当前工作目录。
     *
     * @return 沙箱根目录（绝对路径）
     */
    public Path effectiveBaseDir() {
        return baseDir != null ? baseDir.toAbsolutePath().normalize()
                : Path.of("").toAbsolutePath().normalize();
    }
}