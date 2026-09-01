package com.realapex.tool.doc.engine.template;

import com.realapex.tool.doc.model.DocFormat;
import com.realapex.tool.doc.model.TemplateField;
import com.realapex.tool.doc.model.TemplateSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板 Schema 探查器——扫描模板占位符，输出结构化字段清单。
 *
 * <p>支持两种模板语法：</p>
 * <ul>
 *   <li>.docx（poi-tl Mustache 风格）：{@code {{var}}} 文本、{@code {{#list}}...{{/list}} 循环表格、{@code {{@img}} 图片}</li>
 *   <li>.xlsx（EasyExcel 风格）：{@code {var}} 文本、{@code {.list}} 列表</li>
 * </ul>
 *
 * <p>输出 {@link TemplateSchema} 供大模型按字段清单准备渲染数据。</p>
 */
@Slf4j
public final class TemplateSchemaInspector {

    /** poi-tl 占位符正则：{{var}} / {{#list}} / {{/list}} / {{@img}} */
    private static final Pattern POI_TL_PATTERN = Pattern.compile("\\{\\{([#/@]?)([^{}]+?)\\}\\}");

    /** EasyExcel 占位符正则：{var} / {.list} */
    private static final Pattern EASY_EXCEL_PATTERN = Pattern.compile("\\{(\\.?)([^{}]+?)\\}");

    /**
     * 探查模板占位符，生成结构化 Schema。
     *
     * @param templateFile 模板文件（.docx / .xlsx）
     * @return 模板 Schema（字段清单）
     * @throws Exception 模板解析失败时抛出
     */
    public TemplateSchema inspect(Path templateFile) throws Exception {
        DocFormat format = DocFormat.fromExtension(templateFile.getFileName().toString());
        if (format == DocFormat.DOCX) {
            return inspectDocx(templateFile);
        }
        if (format == DocFormat.XLSX) {
            return inspectXlsx(templateFile);
        }
        throw new IllegalArgumentException("不支持的模板格式（仅支持 .docx / .xlsx）: " + templateFile);
    }

    /**
     * 探查 .docx 模板（poi-tl 占位符）。
     */
    private TemplateSchema inspectDocx(Path templateFile) throws Exception {
        Map<String, TemplateField> fields = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(templateFile);
             XWPFDocument doc = new XWPFDocument(in)) {

            for (var element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    collectFromText(paragraph.getText(), fields, null);
                } else if (element instanceof XWPFTable table) {
                    collectFromTable(table, fields);
                }
            }
        }
        return TemplateSchema.builder()
                .template(templateFile.getFileName().toString())
                .fields(new ArrayList<>(fields.values()))
                .build();
    }

    /**
     * 探查 .xlsx 模板（EasyExcel 占位符）。
     * <p>EasyExcel 模板为 XML 结构，此处直接扫描 sheet XML 文本中的 {@code {var}} 占位符。</p>
     */
    private TemplateSchema inspectXlsx(Path templateFile) throws Exception {
        Map<String, TemplateField> fields = new LinkedHashMap<>();
        String xml = new String(Files.readAllBytes(templateFile), java.nio.charset.StandardCharsets.UTF_8);
        Matcher matcher = EASY_EXCEL_PATTERN.matcher(xml);
        while (matcher.find()) {
            String listMark = matcher.group(1);
            String name = matcher.group(2).trim();
            if (name.isEmpty()) {
                continue;
            }
            String type = listMark.equals(".") ? "table" : "text";
            fields.putIfAbsent(name, TemplateField.builder()
                    .name(name)
                    .type(type)
                    .required(false)
                    .build());
        }
        return TemplateSchema.builder()
                .template(templateFile.getFileName().toString())
                .fields(new ArrayList<>(fields.values()))
                .build();
    }

    /**
     * 从文本中收集占位符。
     *
     * @param text   文本内容
     * @param fields 字段集合（按名去重）
     * @param columns 循环表格列名（table 类型时传入）
     */
    private void collectFromText(String text, Map<String, TemplateField> fields, List<String> columns) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = POI_TL_PATTERN.matcher(text);
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String name = matcher.group(2).trim();
            if (name.isEmpty()) {
                continue;
            }
            // 循环结束标记 {{/name}} 跳过
            if (prefix.equals("/")) {
                continue;
            }
            String type = switch (prefix) {
                case "#" -> "table";
                case "@" -> "image";
                default -> "text";
            };
            fields.putIfAbsent(name, TemplateField.builder()
                    .name(name)
                    .type(type)
                    .required(false)
                    .columns(type.equals("table") ? columns : null)
                    .build());
        }
    }

    /**
     * 从表格中收集占位符（含循环表格列名提取）。
     */
    private void collectFromTable(XWPFTable table, Map<String, TemplateField> fields) {
        List<String> headerColumns = null;
        List<XWPFTableRow> rows = table.getRows();
        if (!rows.isEmpty()) {
            headerColumns = new ArrayList<>();
            for (XWPFTableCell cell : rows.get(0).getTableCells()) {
                headerColumns.add(cell.getText().trim());
            }
        }
        for (XWPFTableRow row : rows) {
            for (XWPFTableCell cell : row.getTableCells()) {
                collectFromText(cell.getText(), fields, headerColumns);
            }
        }
    }
}