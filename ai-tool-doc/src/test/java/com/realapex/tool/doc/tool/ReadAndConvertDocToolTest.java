package com.realapex.tool.doc.tool;

import com.realapex.tool.doc.config.DocToolConfig;
import com.realapex.tool.doc.model.DocConvertRequest;
import com.realapex.tool.doc.model.DocConvertResult;
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
 * {@link ReadAndConvertDocTool} 单元测试——端到端文档转换。
 */
class ReadAndConvertDocToolTest {

    @TempDir
    Path tempDir;

    private Path createDocx(String text) throws Exception {
        Path file = tempDir.resolve("sample.docx");
        try (XWPFDocument doc = new XWPFDocument();
             OutputStream out = Files.newOutputStream(file)) {
            XWPFParagraph p = doc.createParagraph();
            p.createRun().setText(text);
            doc.write(out);
        }
        return file;
    }

    @Test
    void execute_shouldConvertDocxToMarkdown() throws Exception {
        Path file = createDocx("Hello AI Workbench");
        DocToolConfig config = DocToolConfig.builder()
                .baseDir(tempDir)
                .build();
        ReadAndConvertDocTool tool = new ReadAndConvertDocTool(config);

        DocConvertResult result = tool.execute(new DocConvertRequest(
                "sample.docx", null, null, null));

        assertEquals("docx", result.getFormat());
        assertTrue(result.getMarkdown().contains("Hello AI Workbench"), "应提取文档文本");
    }

    @Test
    void execute_shouldRejectMissingFile() {
        DocToolConfig config = DocToolConfig.builder()
                .baseDir(tempDir)
                .build();
        ReadAndConvertDocTool tool = new ReadAndConvertDocTool(config);

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                () -> tool.execute(new DocConvertRequest("missing.docx", null, null, null)));
    }
}