package com.realapex.tool.doc.engine.template;

import com.realapex.tool.doc.model.RenderResult;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WordTemplateRenderer} 单元测试——poi-tl 文本占位符渲染。
 */
class WordTemplateRendererTest {

    @TempDir
    Path tempDir;

    private Path createTemplate(String text) throws Exception {
        Path file = tempDir.resolve("template.docx");
        try (XWPFDocument doc = new XWPFDocument();
             OutputStream out = Files.newOutputStream(file)) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText(text);
            doc.write(out);
        }
        return file;
    }

    @Test
    void render_shouldReplaceTextPlaceholder() throws Exception {
        Path template = createTemplate("尊敬的 {{customer}}，您的订单 {{orderNo}} 已发货。");
        Path output = tempDir.resolve("output.docx");

        RenderResult result = new WordTemplateRenderer().render(template,
                Map.of("customer", "张三", "orderNo", "A1001"), output);

        assertEquals("docx", result.getFormat());
        assertTrue(Files.exists(output), "输出文件应生成");
        assertTrue(result.getSizeBytes() > 0, "输出文件应有大小");

        // 验证渲染结果：重新读取输出文档，占位符应被替换
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(output))) {
            String text = doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .reduce("", String::concat);
            assertTrue(text.contains("张三"), "占位符应被替换为数据值");
            assertTrue(text.contains("A1001"), "订单号应被替换");
            assertTrue(!text.contains("{{customer}}"), "占位符不应残留");
        }
    }
}