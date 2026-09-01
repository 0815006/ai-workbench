package com.realapex.tool.doc.engine.convert;

import com.realapex.tool.doc.model.DocFormat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 转换器工厂——按格式路由到对应 {@link DocumentConverter} 实现。
 * <p>内部持有单例缓存，避免每次转换重复创建 POI/PDFBox 相关对象。</p>
 */
public final class ConverterFactory {

    private static final Map<DocFormat, DocumentConverter> CACHE = new ConcurrentHashMap<>();

    private ConverterFactory() {
    }

    /**
     * 按格式获取转换器（首次创建后缓存）。
     *
     * @param format 文档格式
     * @return 对应转换器
     * @throws IllegalArgumentException 不支持的格式时抛出
     */
    public static DocumentConverter get(DocFormat format) {
        if (format == null || format == DocFormat.UNKNOWN) {
            throw new IllegalArgumentException("无法识别的文档格式: " + format);
        }
        return CACHE.computeIfAbsent(format, ConverterFactory::create);
    }

    /**
     * 按文件自动探测格式并获取转换器。
     *
     * @param file 待转换文件
     * @return 对应转换器
     * @throws Exception 探测失败时抛出
     */
    public static DocumentConverter detect(Path file) throws Exception {
        return get(FormatDetector.detect(file));
    }

    /**
     * 创建转换器实例。
     *
     * @param format 文档格式
     * @return 转换器实例
     */
    private static DocumentConverter create(DocFormat format) {
        return switch (format) {
            case DOC, DOCX -> new WordConverter();
            case XLS, XLSX -> new ExcelConverter();
            case PDF -> new PdfConverter();
            default -> throw new IllegalArgumentException("不支持的文档格式: " + format);
        };
    }

    /**
     * 获取全部已注册的转换器（供测试/审计使用）。
     *
     * @return 转换器列表
     */
    public static List<DocumentConverter> all() {
        return List.of(new WordConverter(), new ExcelConverter(), new PdfConverter());
    }
}