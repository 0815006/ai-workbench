package com.realapex.tool.doc.engine.convert;

import com.realapex.tool.doc.model.DocConvertOptions;
import com.realapex.tool.doc.model.DocConvertResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PdfConverter} 单元测试——逐页提取、段落合并与扫描件警告。
 */
class PdfConverterTest {

    @TempDir
    Path tempDir;

    private Path createPdf(String text, int pages) throws Exception {
        Path file = tempDir.resolve("sample.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(50, 700);
                    cs.showText(text + " - page " + (i + 1));
                    cs.endText();
                }
            }
            doc.save(file.toFile());
        }
        return file;
    }

    private Path createBlankPdf() throws Exception {
        Path file = tempDir.resolve("blank.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(file.toFile());
        }
        return file;
    }

    @Test
    void convert_shouldExtractTextPerPage() throws Exception {
        Path file = createPdf("Hello World", 2);
        DocConvertResult result = new PdfConverter().convert(file, DocConvertOptions.defaults());

        assertEquals("pdf", result.getFormat());
        assertEquals(2, result.getPageCount());
        assertTrue(result.getMarkdown().contains("## Page 1"), "应渲染 Page 1 分段");
        assertTrue(result.getMarkdown().contains("## Page 2"), "应渲染 Page 2 分段");
        assertTrue(result.getMarkdown().contains("Hello World"), "应提取文本");
    }

    @Test
    void convert_shouldTruncatePagesBeyondMaxPages() throws Exception {
        Path file = createPdf("content", 5);
        DocConvertOptions options = DocConvertOptions.builder().maxPages(2).build();
        DocConvertResult result = new PdfConverter().convert(file, options);

        assertTrue(result.isTruncated(), "超过 maxPages 应标记截断");
        assertEquals(2, result.getPageCount());
        assertTrue(result.getMarkdown().contains("截断"), "应包含截断提示");
    }

    @Test
    void convert_shouldWarnOnScannedPdf() throws Exception {
        Path file = createBlankPdf();
        DocConvertResult result = new PdfConverter().convert(file, DocConvertOptions.defaults());

        assertNotNull(result.getWarning(), "扫描件应产生警告");
        assertTrue(result.getWarning().contains("OCR"), "警告应提示需 OCR");
    }
}