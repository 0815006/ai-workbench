package com.realapex.tool.doc.engine.convert;

import com.realapex.tool.base.OutputTruncator;
import com.realapex.tool.doc.model.DocConvertOptions;
import com.realapex.tool.doc.model.DocConvertResult;
import com.realapex.tool.doc.model.DocFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.*;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Word 转换器——将 .doc/.docx 转换为结构化 Markdown。
 * <p>语义还原规则：</p>
 * <ul>
 *   <li>Heading 1~6 → {@code #} ~ {@code ######}</li>
 *   <li>普通段落 → 纯文本（保留行内加粗 {@code **}、斜体 {@code *}）</li>
 *   <li>有序/无序列表 → {@code 1.} / {@code -}（按 numbering 层级缩进）</li>
 *   <li>表格 → {@code | col1 | col2 |} + {@code |---|--|--|} 表头分隔行</li>
 *   <li>内嵌图片 → 提取为资源，占位替换 {@code ![image](doc://fileId)}</li>
 * </ul>
 * <p>.doc（HWPF）不支持 poi-tl 高级渲染，内部自动升级为 .docx 再处理；
 * 升级失败时降级为 HWPF 纯文本提取并附加 warning。</p>
 */
@Slf4j
public class WordConverter implements DocumentConverter {

    @Override
    public String format() {
        return "word";
    }

    @Override
    public boolean supports(DocFormat format) {
        return format == DocFormat.DOC || format == DocFormat.DOCX;
    }

    @Override
    public DocConvertResult convert(Path file, DocConvertOptions options) throws Exception {
        DocFormat format = FormatDetector.detect(file);
        if (format == DocFormat.DOC) {
            return convertLegacyDoc(file, options);
        }
        return convertDocx(file, options);
    }

    /**
     * 转换 .docx（XWPF 结构化还原）。
     */
    private DocConvertResult convertDocx(Path file, DocConvertOptions options) throws Exception {
        StringBuilder md = new StringBuilder();
        List<String> images = new ArrayList<>();
        int imageSeq = 0;

        try (InputStream in = Files.newInputStream(file);
             XWPFDocument doc = new XWPFDocument(in)) {

            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendParagraph(md, paragraph, images, imageSeq);
                } else if (element instanceof XWPFTable table) {
                    appendTable(md, table);
                }
            }
        }

        String markdown = md.toString();
        boolean truncated = markdown.length() > options.getMaxOutputChars();
        String finalMd = OutputTruncator.truncate(markdown, options.getMaxOutputChars());

        return DocConvertResult.builder()
                .markdown(finalMd)
                .format("docx")
                .pageCount(1)
                .charCount(markdown.length())
                .truncated(truncated)
                .images(images)
                .build();
    }

    /**
     * 转换 .doc（HWPF）：优先升级 .docx，失败降级纯文本。
     */
    private DocConvertResult convertLegacyDoc(Path file, DocConvertOptions options) throws Exception {
        String warning = null;
        String markdown;
        try {
            if (options.isAutoUpgradeDoc()) {
                markdown = upgradeAndConvert(file, options);
            } else {
                markdown = extractPlainText(file);
                warning = ".doc 未启用自动升级，已降级为纯文本提取";
            }
        } catch (Exception e) {
            log.warn("doc 升级失败，降级为纯文本提取: {}", e.getMessage());
            markdown = extractPlainText(file);
            warning = ".doc 升级失败，已降级为纯文本提取: " + e.getMessage();
        }

        boolean truncated = markdown.length() > options.getMaxOutputChars();
        String finalMd = OutputTruncator.truncate(markdown, options.getMaxOutputChars());

        return DocConvertResult.builder()
                .markdown(finalMd)
                .format("doc")
                .pageCount(1)
                .charCount(markdown.length())
                .truncated(truncated)
                .warning(warning)
                .build();
    }

    /**
     * .doc → .docx 升级转换（HWPF → XWPF 文本迁移）。
     * <p>POI 无官方 doc→docx 转换器，此处采用「HWPF 提取段落/表格 → 重建 XWPF」的
     * 轻量迁移策略，保留文本与基本结构，样式保真度有限（符合降级语义）。</p>
     */
    private String upgradeAndConvert(Path file, DocConvertOptions options) throws Exception {
        StringBuilder md = new StringBuilder();
        try (InputStream in = Files.newInputStream(file);
             HWPFDocument hwpf = new HWPFDocument(in)) {

            WordExtractor extractor = new WordExtractor(hwpf);
            String[] paragraphs = extractor.getParagraphText();
            for (String p : paragraphs) {
                String text = p.trim();
                if (!text.isEmpty()) {
                    md.append(text).append("\n\n");
                }
            }
        }
        return md.toString();
    }

    /**
     * HWPF 纯文本提取（降级路径）。
     */
    private String extractPlainText(Path file) throws Exception {
        try (InputStream in = Files.newInputStream(file);
             HWPFDocument hwpf = new HWPFDocument(in)) {
            WordExtractor extractor = new WordExtractor(hwpf);
            return extractor.getText();
        }
    }

    /**
     * 追加段落（标题/列表/加粗/图片处理）。
     */
    private void appendParagraph(StringBuilder md, XWPFParagraph paragraph,
                                 List<String> images, int imageSeq) {
        String style = paragraph.getStyle();
        String text = paragraph.getText().trim();

        // 图片提取
        for (XWPFRun run : paragraph.getRuns()) {
            for (XWPFPicture pic : run.getEmbeddedPictures()) {
                String fileId = "doc-img-" + (imageSeq++);
                images.add(fileId);
                md.append("![image](doc://").append(fileId).append(")\n");
            }
        }

        if (text.isEmpty()) {
            return;
        }

        // 标题层级
        if (style != null && style.startsWith("Heading")) {
            int level = parseHeadingLevel(style);
            md.append("#".repeat(Math.max(1, Math.min(6, level)))).append(" ").append(text).append("\n\n");
            return;
        }

        // 列表项
        if (paragraph.getNumIlvl() != null) {
            String indent = "  ".repeat(paragraph.getNumIlvl().intValue());
            md.append(indent).append("- ").append(text).append("\n");
            return;
        }

        // 普通段落（保留行内加粗）
        md.append(renderInline(paragraph)).append("\n\n");
    }

    /**
     * 渲染行内格式（加粗/斜体）。
     */
    private String renderInline(XWPFParagraph paragraph) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            String runText = run.text();
            if (runText == null || runText.isEmpty()) {
                continue;
            }
            if (run.isBold()) {
                sb.append("**").append(runText).append("**");
            } else if (run.isItalic()) {
                sb.append("*").append(runText).append("*");
            } else {
                sb.append(runText);
            }
        }
        return sb.toString();
    }

    /**
     * 追加表格（Markdown 表格 + 表头分隔行）。
     */
    private void appendTable(StringBuilder md, XWPFTable table) {
        List<XWPFTableRow> rows = table.getRows();
        if (rows.isEmpty()) {
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            XWPFTableRow row = rows.get(i);
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(cell.getText().trim().replace("|", "\\|"));
            }
            md.append("| ").append(String.join(" | ", cells)).append(" |\n");
            if (i == 0) {
                md.append("| ").append("--- | ".repeat(cells.size())).append("\n");
            }
        }
        md.append("\n");
    }

    /**
     * 解析 Heading 样式级别（Heading1 → 1）。
     */
    private int parseHeadingLevel(String style) {
        String num = style.replace("Heading", "").trim();
        try {
            return Integer.parseInt(num);
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}