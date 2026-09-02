package com.realapex.tool.doc.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realapex.tool.annotation.Tool;
import com.realapex.tool.base.PathSafety;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.doc.config.DocToolConfig;
import com.realapex.tool.doc.engine.template.ExcelTemplateRenderer;
import com.realapex.tool.doc.engine.template.WordTemplateRenderer;
import com.realapex.tool.doc.model.DocFormat;
import com.realapex.tool.doc.model.RenderRequest;
import com.realapex.tool.doc.model.RenderResult;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 文档渲染工具（{@code render_document}）。
 * <p>将结构化数据（JSON）渲染进 .docx / .xlsx 模板，生成新文档：</p>
 * <ul>
 *   <li>.docx → poi-tl 高保真渲染（文本/图片/循环表格）</li>
 *   <li>.xlsx → EasyExcel fill 模板填充（单值/列表循环）</li>
 * </ul>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>模板路径沙箱校验（{@link PathSafety#resolveSafePath}）</li>
 *   <li>输出路径必须位于 outputDir 内，且禁止与输入模板相同（防覆盖）</li>
 *   <li>输出文件名冲突时自动追加时间戳后缀</li>
 *   <li>写操作默认不触发 HITL（生成的是用户自己的工作产物），可通过
 *       {@code DocToolConfig.renderRequiresApproval=true} 提升为需审批</li>
 * </ul>
 */
@Slf4j
@Tool(name = "render_document",
        description = "将结构化数据（JSON）渲染进 .docx/.xlsx 模板生成新文档，"
                + "支持 poi-tl 文本/图片/循环表格与 EasyExcel 模板填充",
        readOnly = false)
public class RenderDocumentTool implements AgentTool<RenderRequest, RenderResult> {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final DocToolConfig config;
    private final WordTemplateRenderer wordRenderer;
    private final ExcelTemplateRenderer excelRenderer;
    private final ObjectMapper objectMapper;

    public RenderDocumentTool(DocToolConfig config,
                              WordTemplateRenderer wordRenderer,
                              ExcelTemplateRenderer excelRenderer,
                              ObjectMapper objectMapper) {
        this.config = config;
        this.wordRenderer = wordRenderer;
        this.excelRenderer = excelRenderer;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "render_document";
    }

    @Override
    public String description() {
        return "将结构化数据（JSON）渲染进 .docx/.xlsx 模板生成新文档，"
                + "支持 poi-tl 文本/图片/循环表格与 EasyExcel 模板填充；"
                + "输出位于沙箱 outputDir 内，禁止覆盖输入模板";
    }

    @Override
    public Class<RenderRequest> requestClass() {
        return RenderRequest.class;
    }

    @Override
    public boolean requiresApproval() {
        return config.isRenderRequiresApproval();
    }

    @Override
    public RenderResult execute(RenderRequest request) throws Exception {
        // 1. 模板路径沙箱校验
        Path template = PathSafety.resolveSafePath(config.effectiveBaseDir(), request.templatePath());
        if (!Files.exists(template)) {
            throw new IllegalArgumentException("模板文件不存在: " + template);
        }

        // 2. 解析渲染数据 JSON
        Map<String, Object> data = objectMapper.readValue(request.dataJson(),
                new TypeReference<Map<String, Object>>() {
                });

        // 3. 确定输出路径（防覆盖 + 时间戳去冲突）
        Path output = resolveOutputPath(request, template);
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }

        // 4. 按模板格式路由渲染
        DocFormat format = DocFormat.fromExtension(template.getFileName().toString());
        RenderResult result = switch (format) {
            case DOCX -> wordRenderer.render(template, data, output);
            case XLSX -> excelRenderer.render(template, data, output);
            default -> throw new IllegalArgumentException(
                    "不支持的模板格式（仅 .docx / .xlsx）: " + format);
        };

        log.info("render_document: template={} -> output={} ({} bytes)",
                template, result.getOutputPath(), result.getSizeBytes());
        return result;
    }

    /**
     * 解析输出路径：用户指定则沙箱校验；未指定则 outputDir 下自动命名；
     * 已存在时追加时间戳后缀，禁止与模板路径相同。
     *
     * @param request  渲染请求
     * @param template 模板路径（绝对）
     * @return 输出文件路径（绝对）
     * @throws IllegalArgumentException 输出路径越界或与模板相同时抛出
     */
    private Path resolveOutputPath(RenderRequest request, Path template) {
        Path outputDir = config.effectiveOutputDir();
        Path output;
        if (request.outputPath() != null && !request.outputPath().isBlank()) {
            output = PathSafety.resolveSafePath(outputDir, request.outputPath());
        } else {
            String baseName = template.getFileName().toString();
            output = outputDir.resolve(baseName);
        }

        // 禁止覆盖输入模板
        if (output.toAbsolutePath().normalize().equals(template.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("输出路径与输入模板相同，禁止覆盖: " + output);
        }

        // 已存在 → 追加时间戳后缀
        if (Files.exists(output)) {
            String fileName = output.getFileName().toString();
            int dot = fileName.lastIndexOf('.');
            String stem = dot > 0 ? fileName.substring(0, dot) : fileName;
            String ext = dot > 0 ? fileName.substring(dot) : "";
            String ts = LocalDateTime.now().format(TS_FORMAT);
            output = output.resolveSibling(stem + "_" + ts + ext);
        }
        return output;
    }
}