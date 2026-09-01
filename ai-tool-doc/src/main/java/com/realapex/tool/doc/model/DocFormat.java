package com.realapex.tool.doc.model;

import java.util.Locale;

/**
 * 文档格式枚举——统一归一化 Word/Excel/PDF 格式标识。
 * <p>由 {@code FormatDetector} 根据扩展名 / 魔数 / MIME 探测后归一化，
 * 供 {@code ConverterFactory} 路由到对应 Converter。</p>
 *
 * <h3>格式说明</h3>
 * <ul>
 *   <li>{@code DOC}：Word 97-2003（HWPF），解析前自动升级为 DOCX</li>
 *   <li>{@code DOCX}：Word 2007+（XWPF），支持 poi-tl 模板渲染</li>
 *   <li>{@code XLS}：Excel 97-2003（HSSF），流式解析</li>
 *   <li>{@code XLSX}：Excel 2007+（XSSF），SAX 流式解析</li>
 *   <li>{@code PDF}：PDF 文档（PDFBox 逐页提取）</li>
 *   <li>{@code UNKNOWN}：无法识别的格式</li>
 * </ul>
 */
public enum DocFormat {

    /** Word 97-2003 二进制格式 */
    DOC,
    /** Word 2007+ OOXML 格式 */
    DOCX,
    /** Excel 97-2003 二进制格式 */
    XLS,
    /** Excel 2007+ OOXML 格式 */
    XLSX,
    /** PDF 文档 */
    PDF,
    /** 无法识别的格式 */
    UNKNOWN;

    /**
     * 根据文件扩展名推断格式（不区分大小写）。
     *
     * @param fileName 文件名（可含路径）
     * @return 推断出的格式；无法识别返回 {@link #UNKNOWN}
     */
    public static DocFormat fromExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return UNKNOWN;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".doc")) {
            return DOC;
        }
        if (lower.endsWith(".docx")) {
            return DOCX;
        }
        if (lower.endsWith(".xls")) {
            return XLS;
        }
        if (lower.endsWith(".xlsx")) {
            return XLSX;
        }
        if (lower.endsWith(".pdf")) {
            return PDF;
        }
        return UNKNOWN;
    }

    /**
     * 根据 MIME 类型推断格式。
     *
     * @param mime MIME 类型（如 application/pdf）
     * @return 推断出的格式；无法识别返回 {@link #UNKNOWN}
     */
    public static DocFormat fromMime(String mime) {
        if (mime == null || mime.isBlank()) {
            return UNKNOWN;
        }
        String lower = mime.toLowerCase(Locale.ROOT);
        if (lower.contains("pdf")) {
            return PDF;
        }
        if (lower.contains("wordprocessingml") || lower.contains("msword")) {
            return lower.contains("template") ? DOCX : DOCX;
        }
        if (lower.contains("spreadsheetml") || lower.contains("excel")) {
            return lower.contains("template") ? XLSX : XLSX;
        }
        return UNKNOWN;
    }

    /**
     * 是否为 Office 二进制旧格式（需要升级/特殊处理）。
     *
     * @return true 表示 DOC 或 XLS
     */
    public boolean isLegacy() {
        return this == DOC || this == XLS;
    }

    /**
     * 是否为 OOXML 新格式。
     *
     * @return true 表示 DOCX 或 XLSX
     */
    public boolean isOoxml() {
        return this == DOCX || this == XLSX;
    }
}