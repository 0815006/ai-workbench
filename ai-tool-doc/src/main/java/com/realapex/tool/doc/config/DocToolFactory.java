package com.realapex.tool.doc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.doc.engine.convert.ConverterFactory;
import com.realapex.tool.doc.engine.template.ExcelTemplateRenderer;
import com.realapex.tool.doc.engine.template.TemplateSchemaInspector;
import com.realapex.tool.doc.engine.template.WordTemplateRenderer;
import com.realapex.tool.doc.security.DocumentPathInterceptor;
import com.realapex.tool.doc.tool.InspectTemplateSchemaTool;
import com.realapex.tool.doc.tool.ReadAndConvertDocTool;
import com.realapex.tool.doc.tool.RenderDocumentTool;
import com.realapex.tool.security.ToolSecurityInterceptor;

import java.util.List;

/**
 * 文档工具工厂——一键挂载 ai-tool-doc 工具组（Tool Set）。
 * <p>与 {@code ai-tool-sdk} 的 {@code BaseToolFactory} 风格一致：
 * 宿主应用无需逐个 {@code new} 工具，直接调用工厂方法即可获得带沙箱防护的完整文档工具组。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * DocToolConfig config = DocToolConfig.builder()
 *         .baseDir(Path.of("/home/workspace/docs"))
 *         .outputDir(Path.of("/home/workspace/docs/output"))
 *         .build();
 *
 * // 一键挂载 3 个文档原子工具
 * List<AgentTool<?, ?>> docTools = DocToolFactory.createDocTools(config);
 *
 * // 领域专属路径拦截器（优先级 5，先于通用链执行）
 * ToolSecurityInterceptor interceptor = DocToolFactory.createPathInterceptor(config);
 * }</pre>
 */
public final class DocToolFactory {

    private DocToolFactory() {
    }

    /**
     * 创建文档工具组（3 个原子工具）。
     * <p>包含：{@code read_and_convert_doc}（文档→Markdown）、
     * {@code inspect_template_schema}（模板占位符探查）、
     * {@code render_document}（数据→模板渲染）。</p>
     *
     * @param config 文档工具配置（沙箱根目录/输出目录/截断参数等）
     * @return 文档工具列表
     */
    public static List<AgentTool<?, ?>> createDocTools(DocToolConfig config) {
        TemplateSchemaInspector schemaInspector = new TemplateSchemaInspector();
        WordTemplateRenderer wordRenderer = new WordTemplateRenderer();
        ExcelTemplateRenderer excelRenderer = new ExcelTemplateRenderer();
        ObjectMapper objectMapper = new ObjectMapper();

        return List.of(
                new ReadAndConvertDocTool(config),
                new InspectTemplateSchemaTool(config, schemaInspector),
                new RenderDocumentTool(config, wordRenderer, excelRenderer, objectMapper)
        );
    }

    /**
     * 创建文档领域专属路径拦截器。
     * <p>优先级 5，先于通用链（ParamValidator=10 / DangerousCommandFilter=20 / TimeoutInterceptor=50）
     * 执行，校验路径沙箱与来源合法性。</p>
     *
     * @param config 文档工具配置
     * @return 路径拦截器
     */
    public static ToolSecurityInterceptor createPathInterceptor(DocToolConfig config) {
        return new DocumentPathInterceptor(config);
    }
}