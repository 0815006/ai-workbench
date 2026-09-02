package com.realapex.tool.doc.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DocFormat} 枚举单元测试——扩展名/MIME 推断与新旧格式判定。
 */
class DocFormatTest {

    @Test
    void fromExtension_shouldMapKnownExtensions() {
        assertEquals(DocFormat.DOC, DocFormat.fromExtension("report.doc"));
        assertEquals(DocFormat.DOCX, DocFormat.fromExtension("report.docx"));
        assertEquals(DocFormat.XLS, DocFormat.fromExtension("data.xls"));
        assertEquals(DocFormat.XLSX, DocFormat.fromExtension("data.xlsx"));
        assertEquals(DocFormat.PDF, DocFormat.fromExtension("manual.pdf"));
    }

    @Test
    void fromExtension_shouldBeCaseInsensitive() {
        assertEquals(DocFormat.DOCX, DocFormat.fromExtension("REPORT.DOCX"));
        assertEquals(DocFormat.PDF, DocFormat.fromExtension("Manual.PDF"));
    }

    @Test
    void fromExtension_shouldReturnUnknownForUnsupported() {
        assertEquals(DocFormat.UNKNOWN, DocFormat.fromExtension("notes.txt"));
        assertEquals(DocFormat.UNKNOWN, DocFormat.fromExtension("archive.zip"));
        assertEquals(DocFormat.UNKNOWN, DocFormat.fromExtension(null));
        assertEquals(DocFormat.UNKNOWN, DocFormat.fromExtension(""));
    }

    @Test
    void fromMime_shouldMapKnownMimes() {
        assertEquals(DocFormat.PDF, DocFormat.fromMime("application/pdf"));
        assertEquals(DocFormat.DOCX, DocFormat.fromMime("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertEquals(DocFormat.DOCX, DocFormat.fromMime("application/msword"));
        assertEquals(DocFormat.XLSX, DocFormat.fromMime("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertEquals(DocFormat.XLSX, DocFormat.fromMime("application/vnd.ms-excel"));
    }

    @Test
    void fromMime_shouldReturnUnknownForUnsupported() {
        assertEquals(DocFormat.UNKNOWN, DocFormat.fromMime("text/plain"));
        assertEquals(DocFormat.UNKNOWN, DocFormat.fromMime(null));
    }

    @Test
    void isLegacy_shouldOnlyMatchDocAndXls() {
        assertTrue(DocFormat.DOC.isLegacy());
        assertTrue(DocFormat.XLS.isLegacy());
        assertFalse(DocFormat.DOCX.isLegacy());
        assertFalse(DocFormat.XLSX.isLegacy());
        assertFalse(DocFormat.PDF.isLegacy());
    }

    @Test
    void isOoxml_shouldOnlyMatchDocxAndXlsx() {
        assertTrue(DocFormat.DOCX.isOoxml());
        assertTrue(DocFormat.XLSX.isOoxml());
        assertFalse(DocFormat.DOC.isOoxml());
        assertFalse(DocFormat.XLS.isOoxml());
        assertFalse(DocFormat.PDF.isOoxml());
    }
}