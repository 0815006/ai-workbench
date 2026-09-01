package com.realapex.tool.doc.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 模板 Schema——模板占位符的结构化清单。
 * <p>输出给大模型，告知「该模板需要哪些字段、字段类型、是否必填」，
 * 使 Agent 能按 Schema 准备渲染数据。</p>
 */
@Data
@Builder
public class TemplateSchema {

    /** 模板文件名 */
    private String template;

    /** 字段清单 */
    private List<TemplateField> fields;
}