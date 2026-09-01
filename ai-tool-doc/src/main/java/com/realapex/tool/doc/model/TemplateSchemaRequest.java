package com.realapex.tool.doc.model;

import com.realapex.tool.annotation.ToolParam;

/**
 * 模板 Schema 探查请求参数（{@code inspect_template_schema} 工具入参）。
 *
 * @param templatePath 模板文件路径（.docx）
 */
public record TemplateSchemaRequest(
        @ToolParam(description = "模板文件路径（.docx）", required = true)
        String templatePath
) {
}