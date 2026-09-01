package com.realapex.tool.doc.config;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;

/**
 * ai-tool-doc 工具包核心配置。
 * <p>统一管理沙箱根目录、输出/临时目录、文档大小卡口、解析截断参数与渲染选项。
 * 通过 {@code DocToolFactory} 一键创建工具组时传入。</p>
 *
 * <h3>安全默认值</h3>
 * <ul>
 *   <li>{@code maxDocSizeBytes}：输入文档大小上限，默认 20MB（文档天然大于代码，区别于 base 的 2MB）</li>
 *   <li>{@code maxOutputChars}：单工具返回结果上限，默认 20,000 字符</li>
 *   <li>{@code maxPages}：PDF/Word 最大解析页数，默认 50</li>
 *   <li>{@code maxRows}/{@code maxCols}：Excel 最大解析行/列数，默认 100/50（防 OOM）</li>
 * </ul>
 */
@Data
@Builder
public class DocToolConfig {

    /** 默认输入文档大小上限：20MB */
    public static final long DEFAULT_MAX_DOC_SIZE_BYTES = 20 * 1024 * 1024L;

    /** 默认单工具返回结果字符上限：20,000 */
    public static final int DEFAULT_MAX_OUTPUT_CHARS = 20_000;

    /** 默认 PDF/Word 最大解析页数：50 */
    public static final int DEFAULT_MAX_PAGES = 50;

    /** 默认 Excel 最大解析行数：100 */
    public static final int DEFAULT_MAX_ROWS = 100;

    /** 默认 Excel 最大解析列数：50 */
    public static final int DEFAULT_MAX_COLS = 50;

    /** 默认下载/解析超时：30 秒 */
    public static final long DEFAULT_TIMEOUT_MS = 30_000;

    /** 沙箱根目录（所有输入/输出路径统一限定在其内） */
    private Path baseDir;

    /** 渲染输出目录（默认 baseDir/output） */
    private Path outputDir;

    /** 临时目录（URL/Base64 下载落盘，默认 baseDir/temp） */
    private Path tempDir;

    /** 输入文档大小上限（字节），默认 20MB */
    @Builder.Default
    private long maxDocSizeBytes = DEFAULT_MAX_DOC_SIZE_BYTES;

    /** 返回结果截断上限（复用 OutputTruncator），默认 20,000 */
    @Builder.Default
    private int maxOutputChars = DEFAULT_MAX_OUTPUT_CHARS;

    /** PDF/Word 最大解析页数，默认 50 */
    @Builder.Default
    private int maxPages = DEFAULT_MAX_PAGES;

    /** Excel 最大解析行数，默认 100 */
    @Builder.Default
    private int maxRows = DEFAULT_MAX_ROWS;

    /** Excel 最大解析列数，默认 50 */
    @Builder.Default
    private int maxCols = DEFAULT_MAX_COLS;

    /** 是否提取文档内图片（默认 true，提取后以 doc://fileId 占位） */
    @Builder.Default
    private boolean extractImages = true;

    /** .doc 是否自动升级为 .docx（默认 true；升级失败降级 HWPF 纯文本） */
    @Builder.Default
    private boolean autoUpgradeDoc = true;

    /** 渲染是否触发 HITL 审批（默认 false，可配置提升为 true） */
    @Builder.Default
    private boolean renderRequiresApproval = false;

    /** 下载/解析超时（毫秒），默认 30 秒 */
    @Builder.Default
    private long timeoutMs = DEFAULT_TIMEOUT_MS;

    /**
     * 获取沙箱根目录，未配置时回退到当前工作目录。
     *
     * @return 沙箱根目录（绝对路径）
     */
    public Path effectiveBaseDir() {
        return baseDir != null ? baseDir.toAbsolutePath().normalize()
                : Path.of("").toAbsolutePath().normalize();
    }

    /**
     * 获取渲染输出目录，未配置时回退到 baseDir/output。
     *
     * @return 输出目录（绝对路径）
     */
    public Path effectiveOutputDir() {
        return outputDir != null ? outputDir.toAbsolutePath().normalize()
                : effectiveBaseDir().resolve("output");
    }

    /**
     * 获取临时目录，未配置时回退到 baseDir/temp。
     *
     * @return 临时目录（绝对路径）
     */
    public Path effectiveTempDir() {
        return tempDir != null ? tempDir.toAbsolutePath().normalize()
                : effectiveBaseDir().resolve("temp");
    }
}