package com.realapex.tool.doc.engine.template;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.excel.write.metadata.fill.FillConfig;
import com.realapex.tool.doc.model.RenderResult;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Excel 模板渲染器——基于 EasyExcel fill 将数据渲染进 .xlsx 模板。
 *
 * <p>支持的模板语法（EasyExcel 风格）：</p>
 * <ul>
 *   <li>{@code {var}} 单值填充</li>
 *   <li>{@code {.list}} 列表循环填充（data 传 List，自动扩行）</li>
 * </ul>
 *
 * <p>渲染走流式写入，避免大结果集 OOM；复杂公式/嵌套场景可回退 JXLS（本期未引入）。</p>
 */
@Slf4j
public final class ExcelTemplateRenderer {

    /**
     * 将数据渲染进 Excel 模板，生成新文件。
     *
     * @param templateFile 模板文件（.xlsx）
     * @param data         渲染数据（Map 单行 / List 多行）
     * @param outputFile   输出文件路径
     * @return 渲染结果（输出路径 + 格式 + 大小）
     * @throws Exception 渲染失败时抛出
     */
    public RenderResult render(Path templateFile, Object data, Path outputFile) throws Exception {
        try (ExcelWriter writer = EasyExcel.write(outputFile.toFile())
                .withTemplate(templateFile.toFile())
                .build()) {
            WriteSheet sheet = EasyExcel.writerSheet().build();
            if (data instanceof List<?> list) {
                FillConfig fillConfig = FillConfig.builder().forceNewRow(Boolean.TRUE).build();
                writer.fill(list, fillConfig, sheet);
            } else if (data instanceof Map<?, ?> map) {
                writer.fill(map, sheet);
            } else {
                writer.fill(data, sheet);
            }
        }

        long sizeBytes = Files.size(outputFile);
        log.info("Excel 模板渲染完成: {} -> {} ({} bytes)", templateFile, outputFile, sizeBytes);
        return RenderResult.builder()
                .outputPath(outputFile.toAbsolutePath().toString())
                .format("xlsx")
                .sizeBytes(sizeBytes)
                .build();
    }
}