package com.realapex.tool.doc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * ai-tool-doc Spring Boot 配置属性（前缀 {@code realapex.tool.doc}）。
 *
 * <p>示例 application.yml：</p>
 * <pre>{@code
 * realapex:
 *   tool:
 *     doc:
 *       base-dir: /home/workspace/docs
 *       output-dir: /home/workspace/docs/output
 *       max-doc-size-bytes: 20971520
 *       max-output-chars: 20000
 *       max-pages: 50
 *       max-rows: 100
 *       max-cols: 50
 *       extract-images: true
 *       auto-upgrade-doc: true
 *       render-requires-approval: false
 *       timeout-ms: 30000
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "realapex.tool.doc")
public class DocToolProperties {

    /** 沙箱根目录（输入/输出路径统一限定在其内） */
    private Path baseDir;

    /** 渲染输出目录（默认 baseDir/output） */
    private Path outputDir;

    /** 临时目录（URL/Base64 下载落盘，默认 baseDir/temp） */
    private Path tempDir;

    /** 输入文档大小上限（字节），默认 20MB */
    private long maxDocSizeBytes = DocToolConfig.DEFAULT_MAX_DOC_SIZE_BYTES;

    /** 返回结果截断上限（字符），默认 20,000 */
    private int maxOutputChars = DocToolConfig.DEFAULT_MAX_OUTPUT_CHARS;

    /** PDF/Word 最大解析页数，默认 50 */
    private int maxPages = DocToolConfig.DEFAULT_MAX_PAGES;

    /** Excel 最大解析行数，默认 100 */
    private int maxRows = DocToolConfig.DEFAULT_MAX_ROWS;

    /** Excel 最大解析列数，默认 50 */
    private int maxCols = DocToolConfig.DEFAULT_MAX_COLS;

    /** 是否提取文档内图片（默认 true） */
    private boolean extractImages = true;

    /** .doc 是否自动升级为 .docx（默认 true） */
    private boolean autoUpgradeDoc = true;

    /** 渲染是否触发 HITL 审批（默认 false，可配置提升） */
    private boolean renderRequiresApproval = false;

    /** 下载/解析超时（毫秒），默认 30 秒 */
    private long timeoutMs = DocToolConfig.DEFAULT_TIMEOUT_MS;

    /**
     * 转换为领域配置对象 {@link DocToolConfig}。
     *
     * @return DocToolConfig 实例
     */
    public DocToolConfig toConfig() {
        return DocToolConfig.builder()
                .baseDir(baseDir)
                .outputDir(outputDir)
                .tempDir(tempDir)
                .maxDocSizeBytes(maxDocSizeBytes)
                .maxOutputChars(maxOutputChars)
                .maxPages(maxPages)
                .maxRows(maxRows)
                .maxCols(maxCols)
                .extractImages(extractImages)
                .autoUpgradeDoc(autoUpgradeDoc)
                .renderRequiresApproval(renderRequiresApproval)
                .timeoutMs(timeoutMs)
                .build();
    }
}