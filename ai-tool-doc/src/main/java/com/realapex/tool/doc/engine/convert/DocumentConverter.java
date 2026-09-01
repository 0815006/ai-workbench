package com.realapex.tool.doc.engine.convert;

import com.realapex.tool.doc.model.DocConvertOptions;
import com.realapex.tool.doc.model.DocConvertResult;
import com.realapex.tool.doc.model.DocFormat;

import java.nio.file.Path;

/**
 * 文档转换器统一接口——将一种格式文档转换为结构化 Markdown。
 * <p>实现类只需关注「某格式 → Markdown」的语义还原（标题层级/表格/列表/图片占位），
 * 格式路由由 {@link ConverterFactory} 负责，来源归一化由 {@link DocSourceResolver} 负责。</p>
 *
 * <h3>实现类</h3>
 * <ul>
 *   <li>{@link WordConverter} — Word → Markdown（.doc 自动升级/降级）</li>
 *   <li>{@link ExcelConverter} — Excel → Markdown（SAX 流式 + 行/列截断）</li>
 *   <li>{@link PdfConverter} — PDF → Markdown（逐页 + 段落修正）</li>
 * </ul>
 */
public interface DocumentConverter {

    /**
     * 支持的格式标识（doc / docx / xls / xlsx / pdf）。
     *
     * @return 格式标识
     */
    String format();

    /**
     * 是否支持该格式。
     *
     * @param format 文档格式
     * @return true 表示支持
     */
    boolean supports(DocFormat format);

    /**
     * 将文档转换为结构化 Markdown。
     *
     * @param file    待转换文档（已归一化到沙箱内的本地路径）
     * @param options 转换选项（截断/图片提取等）
     * @return 转换结果（markdown + 元数据）
     * @throws Exception 解析失败时抛出
     */
    DocConvertResult convert(Path file, DocConvertOptions options) throws Exception;
}