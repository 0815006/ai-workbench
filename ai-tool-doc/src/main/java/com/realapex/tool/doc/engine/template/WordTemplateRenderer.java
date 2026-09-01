package com.realapex.tool.doc.engine.template;

import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.plugin.table.LoopRowTableRenderPolicy;
import com.realapex.tool.doc.model.RenderResult;
import lombok.extern.slf4j.Slf4j;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Word 模板渲染器——基于 poi-tl 将结构化数据高保真渲染进 .docx 模板。
 *
 * <p>支持的模板语法（poi-tl Mustache 风格）：</p>
 * <ul>
 *   <li>{@code {{name}}} 文本</li>
 *   <li>{@code {{@img}}} 图片（data 传 {@code PictureRenderData} 或 byte[]）</li>
 *   <li>{@code {{#list}}...{{/list}} 循环表格（自动扩行，data 传 List）</li>
 * </ul>
 *
 * <p>渲染基于 XML 语法树，不破坏原模板排版；输出文件由调用方保证位于沙箱 outputDir 内。</p>
 */
@Slf4j
public final class WordTemplateRenderer {

    /**
     * 将结构化数据渲染进 .docx 模板，生成新文档。
     *
     * @param templateFile 模板文件（.docx）
     * @param data         渲染数据（键对应模板占位符）
     * @param outputFile   输出文件路径
     * @return 渲染结果（输出路径 + 格式 + 大小）
     * @throws Exception 渲染失败时抛出
     */
    public RenderResult render(Path templateFile, Map<String, Object> data, Path outputFile) throws Exception {
        Configure config = Configure.builder()
                .bind("list", new LoopRowTableRenderPolicy())
                .build();

        try (InputStream in = Files.newInputStream(templateFile);
             OutputStream out = Files.newOutputStream(outputFile)) {
            XWPFTemplate template = XWPFTemplate.compile(in, config);
            template.render(data);
            template.write(out);
            template.close();
        }

        long sizeBytes = Files.size(outputFile);
        log.info("Word 模板渲染完成: {} -> {} ({} bytes)", templateFile, outputFile, sizeBytes);
        return RenderResult.builder()
                .outputPath(outputFile.toAbsolutePath().toString())
                .format("docx")
                .sizeBytes(sizeBytes)
                .build();
    }
}