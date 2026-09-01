package com.realapex.tool.doc.tool;

import com.realapex.tool.annotation.Tool;
import com.realapex.tool.base.PathSafety;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.doc.config.DocToolConfig;
import com.realapex.tool.doc.engine.template.TemplateSchemaInspector;
import com.realapex.tool.doc.model.TemplateSchema;
import com.realapex.tool.doc.model.TemplateSchemaRequest;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模板 Schema 探查工具（{@code inspect_template_schema}）。
 * <p>扫描 .docx / .xlsx 模板中的占位符（poi-tl {@code {{var}}} / EasyExcel {@code {var}}），
 * 输出结构化字段清单（text / table / image），供大模型按 Schema 准备渲染数据。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>路径沙箱：模板路径经 {@link PathSafety#resolveSafePath} 校验</li>
 *   <li>只读工具：不产生任何副作用</li>
 * </ul>
 */
@Slf4j
@Tool(name = "inspect_template_schema",
        description = "探查 .docx/.xlsx 模板占位符，输出结构化字段清单（text/table/image），"
                + "供大模型按 Schema 准备渲染数据",
        readOnly = true)
public class InspectTemplateSchemaTool implements AgentTool<TemplateSchemaRequest, TemplateSchema> {

    private final DocToolConfig config;
    private final TemplateSchemaInspector inspector;

    public InspectTemplateSchemaTool(DocToolConfig config, TemplateSchemaInspector inspector) {
        this.config = config;
        this.inspector = inspector;
    }

    @Override
    public String name() {
        return "inspect_template_schema";
    }

    @Override
    public String description() {
        return "探查 .docx/.xlsx 模板占位符，输出结构化字段清单（text/table/image），"
                + "供大模型按 Schema 准备渲染数据";
    }

    @Override
    public Class<TemplateSchemaRequest> requestClass() {
        return TemplateSchemaRequest.class;
    }

    @Override
    public TemplateSchema execute(TemplateSchemaRequest request) throws Exception {
        Path template = PathSafety.resolveSafePath(config.effectiveBaseDir(), request.templatePath());
        if (!Files.exists(template)) {
            throw new IllegalArgumentException("模板文件不存在: " + template);
        }
        log.info("inspect_template_schema: template={}", template);
        return inspector.inspect(template);
    }
}