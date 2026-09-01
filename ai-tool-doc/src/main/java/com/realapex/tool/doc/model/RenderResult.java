package com.realapex.tool.doc.model;

import lombok.Builder;
import lombok.Data;

/**
 * 文档渲染结果——输出文件信息。
 */
@Data
@Builder
public class RenderResult {

    /** 输出文件绝对路径 */
    private String outputPath;

    /** 输出格式（docx/xlsx） */
    private String format;

    /** 输出文件大小（字节） */
    private long sizeBytes;
}