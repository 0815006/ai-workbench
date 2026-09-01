package com.realapex.tool.doc.engine.convert;

import com.realapex.tool.doc.model.DocFormat;
import org.apache.tika.Tika;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文档格式探测器——扩展名 + 魔数 + Tika 兜底三级判定。
 * <p>判定优先级：<b>扩展名 → 魔数（zip 头 / %PDF）→ Tika content-type</b>。
 * OOXML 系（.docx/.xlsx）本质是 zip，需进一步解析内部条目区分 Word 与 Excel。</p>
 */
public final class FormatDetector {

    private static final Tika TIKA = new Tika();

    private FormatDetector() {
    }

    /**
     * 探测文件格式。
     *
     * @param file 待探测文件
     * @return 归一化格式；无法识别返回 {@link DocFormat#UNKNOWN}
     * @throws IOException 读取失败时抛出
     */
    public static DocFormat detect(Path file) throws IOException {
        // 1. 扩展名优先（最快、最可靠）
        DocFormat byExt = DocFormat.fromExtension(file.getFileName().toString());
        if (byExt != DocFormat.UNKNOWN) {
            return byExt;
        }

        // 2. 魔数判定
        byte[] head = readHead(file, 8);
        if (isPdf(head)) {
            return DocFormat.PDF;
        }
        if (isZip(head)) {
            return detectOoxml(file);
        }

        // 3. Tika 兜底
        String mime = TIKA.detect(file);
        DocFormat byMime = DocFormat.fromMime(mime);
        return byMime != DocFormat.UNKNOWN ? byMime : DocFormat.UNKNOWN;
    }

    /**
     * 读取文件头部字节。
     *
     * @param file 文件
     * @param len  读取长度
     * @return 头部字节数组
     * @throws IOException 读取失败时抛出
     */
    private static byte[] readHead(Path file, int len) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return in.readNBytes(len);
        }
    }

    /**
     * 判断是否为 PDF（魔数 %PDF）。
     *
     * @param head 头部字节
     * @return true 表示 PDF
     */
    private static boolean isPdf(byte[] head) {
        return head.length >= 4
                && head[0] == '%' && head[1] == 'P' && head[2] == 'D' && head[3] == 'F';
    }

    /**
     * 判断是否为 ZIP（魔数 PK\x03\x04，OOXML 系文档本质是 zip）。
     *
     * @param head 头部字节
     * @return true 表示 ZIP
     */
    private static boolean isZip(byte[] head) {
        return head.length >= 4
                && head[0] == 'P' && head[1] == 'K'
                && head[2] == 0x03 && head[3] == 0x04;
    }

    /**
     * 区分 OOXML 系文档：检查 zip 内部条目。
     * <p>存在 {@code word/document.xml} → DOCX；存在 {@code xl/workbook.xml} → XLSX。</p>
     *
     * @param file zip 文件
     * @return DOCX / XLSX / UNKNOWN
     * @throws IOException 读取失败时抛出
     */
    private static DocFormat detectOoxml(Path file) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("word/document.xml".equals(name)) {
                    return DocFormat.DOCX;
                }
                if ("xl/workbook.xml".equals(name)) {
                    return DocFormat.XLSX;
                }
            }
        }
        return DocFormat.UNKNOWN;
    }
}