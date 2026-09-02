package com.realapex.tool.doc.security;

import com.realapex.tool.doc.config.DocToolConfig;
import com.realapex.tool.doc.model.DocConvertRequest;
import com.realapex.tool.doc.model.RenderRequest;
import com.realapex.tool.doc.model.TemplateSchemaRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link DocumentPathInterceptor} 单元测试——路径沙箱、URL 白名单与防覆盖。
 */
class DocumentPathInterceptorTest {

    @TempDir
    Path tempDir;

    private DocumentPathInterceptor interceptor;

    @BeforeEach
    void setUp() {
        DocToolConfig config = DocToolConfig.builder()
                .baseDir(tempDir)
                .outputDir(tempDir)
                .build();
        interceptor = new DocumentPathInterceptor(config);
    }

    @Test
    void priority_shouldBe5() {
        assertEquals(5, interceptor.priority());
    }

    @Test
    void before_shouldAllowLocalPathWithinSandbox() {
        assertDoesNotThrow(() -> interceptor.before("read_and_convert_doc",
                new DocConvertRequest("report.docx", null, null, null)));
    }

    @Test
    void before_shouldRejectPathTraversal() {
        assertThrows(SecurityException.class, () -> interceptor.before("read_and_convert_doc",
                new DocConvertRequest("../outside.docx", null, null, null)));
    }

    @Test
    void before_shouldAllowHttpUrl() {
        assertDoesNotThrow(() -> interceptor.before("read_and_convert_doc",
                new DocConvertRequest("https://example.com/report.pdf", null, null, null)));
    }

    @Test
    void before_shouldRejectFileUrl() {
        assertThrows(SecurityException.class, () -> interceptor.before("read_and_convert_doc",
                new DocConvertRequest("file:///etc/passwd", null, null, null)));
    }

    @Test
    void before_shouldAllowBase64Source() {
        assertDoesNotThrow(() -> interceptor.before("read_and_convert_doc",
                new DocConvertRequest("base64:AAAA", null, null, null)));
    }

    @Test
    void before_shouldRejectBlankSource() {
        assertThrows(SecurityException.class, () -> interceptor.before("read_and_convert_doc",
                new DocConvertRequest("  ", null, null, null)));
    }

    @Test
    void before_shouldValidateTemplatePath() {
        assertDoesNotThrow(() -> interceptor.before("inspect_template_schema",
                new TemplateSchemaRequest("template.docx")));
        assertThrows(SecurityException.class, () -> interceptor.before("inspect_template_schema",
                new TemplateSchemaRequest("../template.docx")));
    }

    @Test
    void before_shouldRejectOutputOutsideOutputDir() {
        RenderRequest render = new RenderRequest("template.docx", "{}", "docx", "../outside.docx");
        assertThrows(SecurityException.class, () -> interceptor.before("render_document", render));
    }

    @Test
    void before_shouldRejectOverwritingTemplate() {
        RenderRequest render = new RenderRequest("template.docx", "{}", "docx", "template.docx");
        assertThrows(SecurityException.class, () -> interceptor.before("render_document", render));
    }

    @Test
    void before_shouldAllowValidRender() {
        RenderRequest render = new RenderRequest("template.docx", "{}", "docx", "output/result.docx");
        assertDoesNotThrow(() -> interceptor.before("render_document", render));
    }
}