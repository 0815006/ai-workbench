package com.realapex.tool.doc.engine.convert;

import com.realapex.tool.base.OutputTruncator;
import com.realapex.tool.doc.model.DocConvertOptions;
import com.realapex.tool.doc.model.DocConvertResult;
import com.realapex.tool.doc.model.DocFormat;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.nio.file.Path;

/**
 * PDF 转换器——将 PDF 逐页提取为结构化 Markdown。
 *
 * <p>语义还原规则：</p>
 * <ul>
 *   <li>逐页提取文本，按页码生成 {@code ## Page N} 分段</li>
 *   <li>段落错位修正：合并被硬换行打断的段落（按行尾标点启发式合并）</li>
 *   <li>{@code maxPages}（默认 50）截断，超限附加「页数已截断」提示</li>
 *   <li>扫描版 PDF（无可提取文本）→ 返回「需 OCR」提示（OCR 不在当前范围）</li>
 * </ul>
 */
@Slf4j
public class PdfConverter implements DocumentConverter {

    @Override
    public String format() {
        return "pdf";
    }

    @Override
    public boolean supports(DocFormat format) {
        return format == DocFormat.PDF;
    }

    @Override
    public DocConvertResult convert(Path file, DocConvertOptions options) throws Exception {
        StringBuilder md = new StringBuilder();
        StringBuilder allText = new StringBuilder();
        int pageCount = 0;
        boolean truncated = false;
        String warning = null;

        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            int totalPages = doc.getNumberOfPages();
            int maxPages = Math.min(options.getMaxPages(), totalPages);
            if (totalPages > options.getMaxPages()) {
                truncated = true;
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            for (int p = 1; p <= maxPages; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String pageText = stripper.getText(doc);
                pageCount++;
                allText.append(pageText);
                md.append("## Page ").append(p).append("\n\n");
                md.append(mergeParagraphs(pageText)).append("\n\n");
            }

            if (truncated) {
                md.append("\n> [!WARNING] PDF 页数已截断：共 ")
                  .append(totalPages).append(" 页，仅显示前 ")
                  .append(options.getMaxPages()).append(" 页\n");
            }

            if (allText.toString().isBlank()) {
                warning = "该 PDF 无可提取文本，疑似扫描件，需 OCR（OCR 不在当前范围）";
            }
        }

        String markdown = md.toString();
        boolean charTruncated = markdown.length() > options.getMaxOutputChars();
        String finalMd = OutputTruncator.truncate(markdown, options.getMaxOutputChars());

        return DocConvertResult.builder()
                .markdown(finalMd)
                .format("pdf")
                .pageCount(pageCount)
                .charCount(markdown.length())
                .truncated(truncated || charTruncated)
                .warning(warning)
                .build();
    }

    /**
     * 段落错位修正：合并被硬换行打断的段落。
     * <p>启发式：行尾无句末标点（。.!?！？；;：:，,、）时与下一行合并。</p>
     *
     * @param pageText 单页原始文本
     * @return 修正后的段落文本
     */
    private String mergeParagraphs(String pageText) {
        String[] lines = pageText.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        boolean prevIncomplete = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                sb.append("\n\n");
                prevIncomplete = false;
                continue;
            }
            if (prevIncomplete) {
                sb.append(trimmed);
            } else {
                if (sb.length() > 0 && !sb.toString().endsWith("\n\n")) {
                    sb.append("\n");
                }
                sb.append(trimmed);
            }
            prevIncomplete = !endsWithSentenceEnd(trimmed);
        }
        return sb.toString();
    }

    /**
     * 判断文本是否以句末标点结尾。
     *
     * @param text 文本
     * @return true 表示以句末标点结尾
     */
    private boolean endsWithSentenceEnd(String text) {
        if (text.isEmpty()) {
            return false;
        }
        char last = text.charAt(text.length() - 1);
        return "。.!?！？；;：:，,、".indexOf(last) >= 0;
    }
}