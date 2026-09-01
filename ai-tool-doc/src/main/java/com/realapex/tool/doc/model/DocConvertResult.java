package com.realapex.tool.doc.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 文档转换结果——结构化 Markdown + 元数据。
 * <p>统一三种解析入口（Word/Excel/PDF）的输出，屏蔽底层 POI/PDFBox 差异。</p>
 */
@Data
@Builder
public class DocConvertResult {

    /** 转换后的 Markdown 文本（含标题层级/表格/列表） */
    private String markdown;

    /** 实际解析的格式（doc/docx/xls/xlsx/pdf） */
    private String format;

    /** 解析页数（PDF/Word）或 Sheet 数（Excel） */
    private int pageCount;

    /** 原始字符数（截断前） */
    private int charCount;

    /** 是否发生截断（页/行/字符任一超限） */
    private boolean truncated;

    /** 提取的图片资源标识列表（doc://fileId） */
    private List<String> images;

    /** 降级/警告信息（如 .doc 降级解析、扫描件需 OCR） */
    private String warning;
}