package com.realapex.tool.doc.engine.convert;

import com.realapex.tool.doc.config.DocToolConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DocSourceResolver} 单元测试——路径/Base64 归一化与大小卡口。
 */
class DocSourceResolverTest {

    @TempDir
    Path tempDir;

    private DocToolConfig config() {
        return DocToolConfig.builder()
                .baseDir(tempDir)
                .maxDocSizeBytes(1024)
                .build();
    }

    @Test
    void resolve_shouldResolveLocalPath() throws Exception {
        Path doc = tempDir.resolve("report.docx");
        Files.writeString(doc, "hello");
        Path resolved = DocSourceResolver.resolve("report.docx", config());
        assertEquals(doc.toAbsolutePath().normalize(), resolved);
    }

    @Test
    void resolve_shouldRejectPathTraversal() {
        assertThrows(SecurityException.class,
                () -> DocSourceResolver.resolve("../outside.txt", config()));
    }

    @Test
    void resolve_shouldDecodeBase64() throws Exception {
        String base64 = Base64.getEncoder().encodeToString("pdf-content".getBytes());
        Path resolved = DocSourceResolver.resolve("base64:" + base64, config());
        assertTrue(Files.exists(resolved), "Base64 应解码落盘");
        assertEquals("pdf-content", Files.readString(resolved));
    }

    @Test
    void resolve_shouldRejectBlankSource() {
        assertThrows(IllegalArgumentException.class,
                () -> DocSourceResolver.resolve("  ", config()));
    }

    @Test
    void resolve_shouldRejectOversizedBase64() {
        String big = Base64.getEncoder().encodeToString(new byte[2048]);
        assertThrows(java.io.IOException.class,
                () -> DocSourceResolver.resolve("base64:" + big, config()));
    }
}