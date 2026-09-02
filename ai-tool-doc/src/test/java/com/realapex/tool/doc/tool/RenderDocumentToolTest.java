package com.realapex.tool.doc.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realapex.tool.doc.config.DocToolConfig;
import com.realapex.tool.doc.engine.template.ExcelTemplateRenderer;
import com.realapex.tool.doc.engine.template.WordTemplateRenderer;
import com.realapex.tool.doc.model.RenderRequest;
import com.realapex.tool.doc.model.RenderResult;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RenderDocumentTool} 单元测试——端到端模板渲染。
 */
class RenderDocumentToolTest {

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
    void execute_shouldRenderDocxTemplate() throws Exception {
        Path template = createTemplate("尊敬的 {{customer}}：");
        DocToolConfig config = DocToolConfig.builder()
                .baseDir(tempDir)
                .outputDir(tempDir.resolve("output"))
                .build();
        RenderDocumentTool tool = new RenderDocumentTool(config,
                new WordTemplateRenderer(), new ExcelTemplateRenderer(), new ObjectMapper());

        RenderResult result = tool.execute(new RenderRequest(
                "template.docx", "{\"customer\":\"李四\"}", "docx", null));

        assertEquals("docx", result.getFormat());
        assertTrue(Files.exists(Path.of(result.getOutputPath())), "输出文件应生成");
        assertTrue(result.getSizeBytes() > 0, "输出文件应有大小");
    }

    @Test
    void execute_shouldRejectMissingTemplate() {
        DocToolConfig config = DocToolConfig.builder()
                .baseDir(tempDir)
                .outputDir(tempDir.resolve("output"))
                .build();
        RenderDocumentTool tool = new RenderDocumentTool(config,
                new WordTemplateRenderer(), new ExcelTemplateRenderer(), new ObjectMapper());

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> tool.execute(new RenderRequest(
                        "missing.docx", "{}", "docx", null)));
    }
}