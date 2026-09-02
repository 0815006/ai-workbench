package com.realapex.tool.doc.engine.convert;

import com.realapex.tool.doc.model.DocFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link FormatDetector} 单元测试——扩展名优先 + 魔数兜底探测。
 */
class FormatDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void detect_shouldPreferExtension() throws Exception {
        Path pdf = tempDir.resolve("doc.pdf");
        Files.writeString(pdf, "not really a pdf but extension wins");
        assertEquals(DocFormat.PDF, FormatDetector.detect(pdf));
    }

    @Test
    void detect_shouldUsePdfMagicNumber() throws Exception {
        Path pdf = tempDir.resolve("noext");
        Files.write(pdf, new byte[]{'%', 'P', 'D', 'F', '-', '1', '.', '7'});
        assertEquals(DocFormat.PDF, FormatDetector.detect(pdf));
    }

    @Test
    void detect_shouldReturnUnknownForPlainText() throws Exception {
        Path txt = tempDir.resolve("notes.txt");
        Files.writeString(txt, "hello world");
        assertEquals(DocFormat.UNKNOWN, FormatDetector.detect(txt));
    }
}