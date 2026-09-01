package com.realapex.tool.doc.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 模板字段——描述模板中的一个占位符。
 * <p>由 {@code TemplateSchemaInspector} 解析 poi-tl 占位符生成，
 * 供大模型按字段清单准备渲染数据。</p>
 */
@Data
@Builder
public class TemplateField {

    /** 字段名（占位符名，如 reportTitle） */
    private String name;

    /** 字段类型：text / table / image */
    private String type;

    /** 是否必填 */
    private boolean required;

    /** 循环表格的列名列表（type=table 时有效） */
    private List<String> columns;
}