package com.realapex.tool.doc.model;

import lombok.Builder;
import lombok.Data;

/**
 * 文档转换选项——控制解析深度与防爆参数。
 * <p>由 {@code DocToolConfig} 派生默认值，工具入参可覆盖部分字段。</p>
 */
@Data
@Builder
public class DocConvertOptions {

    /** 默认 Excel 最大解析行数：100 */
    public static final int DEFAULT_MAX_ROWS = 100;

    /** 默认 Excel 最大解析列数：50 */
    public static final int DEFAULT_MAX_COLS = 50;

    /** 默认 PDF/Word 最大解析页数：50 */
    public static final int DEFAULT_MAX_PAGES = 50;

    /** Excel 最大解析行数（防 OOM） */
    @Builder.Default
    private int maxRows = DEFAULT_MAX_ROWS;

    /** Excel 最大解析列数 */
    @Builder.Default
    private int maxCols = DEFAULT_MAX_COLS;

    /** PDF/Word 最大解析页数 */
    @Builder.Default
    private int maxPages = DEFAULT_MAX_PAGES;

    /** 是否提取文档内图片（提取后以 doc://fileId 占位） */
    @Builder.Default
    private boolean extractImages = true;

    /** .doc 是否自动升级为 .docx（默认 true） */
    @Builder.Default
    private boolean autoUpgradeDoc = true;

    /** 返回结果截断上限（复用 OutputTruncator） */
    @Builder.Default
    private int maxOutputChars = 20_000;

    /**
     * 创建默认转换选项。
     *
     * @return 默认选项实例
     */
    public static DocConvertOptions defaults() {
        return DocConvertOptions.builder().build();
    }
}