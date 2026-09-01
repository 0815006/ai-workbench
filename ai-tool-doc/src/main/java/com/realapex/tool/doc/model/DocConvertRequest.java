package com.realapex.tool.doc.model;

import com.realapex.tool.annotation.ToolParam;

/**
 * 文档转换请求参数（{@code read_and_convert_doc} 工具入参）。
 *
 * @param source    文档来源：本地文件路径 / http(s) URL / Base64 字符串
 * @param format    格式（doc/docx/xls/xlsx/pdf），留空自动探测
 * @param maxRows   Excel 最大解析行数（可选，默认 100，防 OOM）
 * @param maxPages  PDF/Word 最大解析页数（可选，默认 50）
 */
public record DocConvertRequest(
        @ToolParam(description = "文档来源：本地文件路径 / http(s) URL / Base64 字符串", required = true)
        String source,

        @ToolParam(description = "格式（doc/docx/xls/xlsx/pdf），留空自动探测", required = false)
        String format,

        @ToolParam(description = "Excel 最大解析行数（可选，默认 100，防 OOM）", required = false)
        Integer maxRows,

        @ToolParam(description = "PDF/Word 最大解析页数（可选，默认 50）", required = false)
        Integer maxPages
) {
}