package com.realapex.tool.doc.model;

import com.realapex.tool.annotation.ToolParam;

/**
 * 文档渲染请求参数（{@code render_document} 工具入参）。
 *
 * @param templatePath 模板文件路径（.docx/.xlsx）
 * @param dataJson     渲染数据 JSON（键对应模板占位符）
 * @param outputFormat 输出格式（docx/xlsx），留空与模板一致
 * @param outputPath   输出文件路径（可选，默认 outputDir 下自动命名）
 */
public record RenderRequest(
        @ToolParam(description = "模板文件路径（.docx/.xlsx）", required = true)
        String templatePath,

        @ToolParam(description = "渲染数据 JSON（键对应模板占位符）", required = true)
        String dataJson,

        @ToolParam(description = "输出格式（docx/xlsx），留空与模板一致", required = false)
        String outputFormat,

        @ToolParam(description = "输出文件路径（可选，默认 outputDir 下自动命名）", required = false)
        String outputPath
) {
}