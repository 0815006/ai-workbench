package com.realapex.tool.doc.engine.template;

import com.realapex.tool.doc.model.TemplateField;
import com.realapex.tool.doc.model.TemplateSchema;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TemplateSchemaInspector} 单元测试——.docx 占位符探查。
 */
class TemplateSchemaInspectorTest {

    @TempDir
    Path tempDir;

    private Path createDocxTemplate(String... paragraphs) throws Exception {
        Path file = tempDir.resolve("template.docx");
        try (XWPFDocument doc = new XWPFDocument();
             OutputStream out = Files.newOutputStream(file)) {
            for (String text : paragraphs) {
                XWPFParagraph p = doc.createParagraph();
                p.createRun().setText(text);
            }
            doc.write(out);
        }
        return file;
    }

    @Test
    void inspect_shouldCollectTextAndImagePlaceholders() throws Exception {
        Path file = createDocxTemplate(
                "尊敬的 {{customer}}：",
                "您的订单 {{orderNo}} 已发货。",
                "{{@logo}} 品牌图片");
        TemplateSchema schema = new TemplateSchemaInspector().inspect(file);

        assertEquals("template.docx", schema.getTemplate());
        List<TemplateField> fields = schema.getFields();
        assertNotNull(fields);

        TemplateField customer = findField(fields, "customer");
        assertEquals("text", customer.getType());

        TemplateField logo = findField(fields, "logo");
        assertEquals("image", logo.getType());
    }

    @Test
    void inspect_shouldCollectTableLoopPlaceholder() throws Exception {
        Path file = tempDir.resolve("loop.docx");
        try (XWPFDocument doc = new XWPFDocument();
             OutputStream out = Files.newOutputStream(file)) {
            XWPFTable table = doc.createTable(2, 2);
            XWPFTableRow header = table.getRow(0);
            header.getCell(0).setText("商品");
            header.getCell(1).setText("价格");
            XWPFTableRow body = table.getRow(1);
            body.getCell(0).setText("{{#items}}");
            body.getCell(1).setText("{{/items}}");
            doc.write(out);
        }

        TemplateSchema schema = new TemplateSchemaInspector().inspect(file);
        TemplateField items = findField(schema.getFields(), "items");
        assertEquals("table", items.getType());
        assertNotNull(items.getColumns(), "循环表格应提取列名");
        assertTrue(items.getColumns().contains("商品"));
    }

    @Test
    void inspect_shouldRejectUnsupportedFormat() throws Exception {
        Path file = tempDir.resolve("template.pdf");
        Files.writeString(file, "%PDF-1.7");
        assertThrows(IllegalArgumentException.class,
                () -> new TemplateSchemaInspector().inspect(file));
    }

    private TemplateField findField(List<TemplateField> fields, String name) {
        return fields.stream()
                .filter(f -> f.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("字段不存在: " + name));
    }
}