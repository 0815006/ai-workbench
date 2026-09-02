package com.realapex.tool.doc.engine.convert;

import com.realapex.tool.doc.model.DocConvertOptions;
import com.realapex.tool.doc.model.DocConvertResult;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WordConverter} 单元测试——.docx 标题/列表/表格语义还原。
 */
class WordConverterTest {

    @TempDir
    Path tempDir;

    private Path createDocx(String heading, String body, String[][] table) throws Exception {
        Path file = tempDir.resolve("sample.docx");
        try (XWPFDocument doc = new XWPFDocument();
             OutputStream out = Files.newOutputStream(file)) {
            XWPFParagraph h = doc.createParagraph();
            h.setStyle("Heading1");
            h.createRun().setText(heading);

            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText(body);

            if (table != null) {
                XWPFTable t = doc.createTable(table.length, table[0].length);
                for (int r = 0; r < table.length; r++) {
                    XWPFTableRow row = t.getRow(r);
                    for (int c = 0; c < table[r].length; c++) {
                        row.getCell(c).setText(table[r][c]);
                    }
                }
            }
            doc.write(out);
        }
        return file;
    }

    @Test
    void convert_shouldRenderHeadingAndParagraph() throws Exception {
        Path file = createDocx("季度报告", "本季度营收增长 20%。", null);
        DocConvertResult result = new WordConverter().convert(file, DocConvertOptions.defaults());

        assertEquals("docx", result.getFormat());
        assertTrue(result.getMarkdown().contains("# 季度报告"), "标题应渲染为 # 一级标题");
        assertTrue(result.getMarkdown().contains("本季度营收增长 20%"), "正文应保留");
        assertFalse(result.isTruncated());
    }

    @Test
    void convert_shouldRenderTableWithHeaderSeparator() throws Exception {
        String[][] table = {{"产品", "销量"}, {"A", "100"}, {"B", "200"}};
        Path file = createDocx("销售表", "如下：", table);
        DocConvertResult result = new WordConverter().convert(file, DocConvertOptions.defaults());

        String md = result.getMarkdown();
        assertTrue(md.contains("| 产品 | 销量 |"), "表头行应渲染");
        assertTrue(md.contains("| --- | --- |"), "表头分隔行应渲染");
        assertTrue(md.contains("| A | 100 |"), "数据行应渲染");
    }

    @Test
    void convert_shouldTruncateWhenExceedingMaxChars() throws Exception {
        Path file = createDocx("标题", "x".repeat(5000), null);
        DocConvertOptions options = DocConvertOptions.builder().maxOutputChars(100).build();
        DocConvertResult result = new WordConverter().convert(file, options);

        assertTrue(result.isTruncated());
        assertTrue(result.getMarkdown().contains("Content truncated"), "应包含截断提示");
    }
}