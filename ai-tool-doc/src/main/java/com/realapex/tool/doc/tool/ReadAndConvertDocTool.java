package com.realapex.tool.doc.tool;

import com.realapex.tool.annotation.Tool;
import com.realapex.tool.base.OutputTruncator;
import com.realapex.tool.base.PathSafety;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.doc.config.DocToolConfig;
import com.realapex.tool.doc.engine.convert.ConverterFactory;
import com.realapex.tool.doc.engine.convert.DocSourceResolver;
import com.realapex.tool.doc.engine.convert.DocumentConverter;
import com.realapex.tool.doc.model.DocConvertOptions;
import com.realapex.tool.doc.model.DocConvertRequest;
import com.realapex.tool.doc.model.DocConvertResult;
import com.realapex.tool.doc.model.DocFormat;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文档阅读工具（{@code read_and_convert_doc}）。
 * <p>读取 Word/Excel/PDF 文档，转换为带标题层级、表格、列表的标准 Markdown，
 * 供大模型做语义降维理解。支持三种来源：本地路径 / http(s) URL / Base64。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>路径沙箱：本地路径经 {@link PathSafety#resolveSafePath} 校验，禁止穿越</li>
 *   <li>大小卡口：超过 {@code maxDocSizeBytes}（默认 20MB）拒绝解析</li>
 *   <li>防爆三合围：页/行/列截断 + 图片占位 + {@link OutputTruncator} 兜底</li>
 * </ul>
 */
@Slf4j
@Tool(name = "read_and_convert_doc",
        description = "读取 Word/Excel/PDF 文档并转换为结构化 Markdown（标题/表格/列表），"
                + "支持本地路径、http(s) URL、Base64 三种来源，自动截断防 Token 爆炸",
        readOnly = true)
public class ReadAndConvertDocTool implements AgentTool<DocConvertRequest, DocConvertResult> {

    private final DocToolConfig config;

    public ReadAndConvertDocTool(DocToolConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "read_and_convert_doc";
    }

    @Override
    public String description() {
        return "读取 Word/Excel/PDF 文档并转换为结构化 Markdown（标题/表格/列表），"
                + "支持本地路径、http(s) URL、Base64 三种来源，自动截断防 Token 爆炸";
    }

    @Override
    public Class<DocConvertRequest> requestClass() {
        return DocConvertRequest.class;
    }

    @Override
    public DocConvertResult execute(DocConvertRequest request) throws Exception {
        // 1. 来源归一化：路径 / URL / Base64 → 沙箱内本地 Path
        Path file = DocSourceResolver.resolve(request.source(), config);

        // 2. 大小卡口
        long size = Files.size(file);
        if (size > config.getMaxDocSizeBytes()) {
            throw new IllegalArgumentException(String.format(
                    "文档过大（%d bytes > 上限 %d bytes），拒绝解析", size, config.getMaxDocSizeBytes()));
        }

        // 3. 格式探测（入参指定优先，否则自动探测）
        DocFormat format = request.format() == null || request.format().isBlank()
                ? DocFormat.fromExtension(file.getFileName().toString())
                : DocFormat.fromExtension(request.format());
        if (format == DocFormat.UNKNOWN) {
            format = DocFormat.fromExtension(file.getFileName().toString());
        }

        // 4. 路由到对应 Converter
        DocumentConverter converter = ConverterFactory.get(format);
        if (converter == null) {
            throw new IllegalArgumentException("不支持的文档格式: " + format);
        }

        // 5. 组装选项（入参覆盖默认值）
        DocConvertOptions options = DocConvertOptions.builder()
                .maxRows(request.maxRows() != null ? request.maxRows() : config.getMaxRows())
                .maxPages(request.maxPages() != null ? request.maxPages() : config.getMaxPages())
                .maxCols(config.getMaxCols())
                .extractImages(config.isExtractImages())
                .autoUpgradeDoc(config.isAutoUpgradeDoc())
                .maxOutputChars(config.getMaxOutputChars())
                .build();

        log.info("read_and_convert_doc: format={}, source={}", format, request.source());
        return converter.convert(file, options);
    }
}