package com.realapex.tool.doc.security;

import com.realapex.tool.base.PathSafety;
import com.realapex.tool.doc.config.DocToolConfig;
import com.realapex.tool.doc.model.DocConvertRequest;
import com.realapex.tool.doc.model.RenderRequest;
import com.realapex.tool.doc.model.TemplateSchemaRequest;
import com.realapex.tool.security.ToolSecurityInterceptor;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

/**
 * 文档领域专属安全拦截器——路径沙箱与来源合法性校验。
 *
 * <p>优先级 5，先于通用链（ParamValidator=10 / DangerousCommandFilter=20 / TimeoutInterceptor=50）执行：</p>
 * <ul>
 *   <li>本地路径：复用 {@link PathSafety#resolveSafePath} 校验，拦截 {@code ../} 路径穿越</li>
 *   <li>URL 来源：仅允许 http/https，拒绝 file:// 与其它协议</li>
 *   <li>Base64 来源：仅校验非空与大小上限（解码落盘由 {@code DocSourceResolver} 负责）</li>
 *   <li>渲染输出：必须位于 {@code outputDir} 沙箱内，且禁止与输入模板路径相同（防覆盖）</li>
 * </ul>
 *
 * <p>仅注册在 ai-tool-doc 内部，不影响 ai-tool-sdk 通用拦截器链。</p>
 */
@Slf4j
public class DocumentPathInterceptor implements ToolSecurityInterceptor {

    private final DocToolConfig config;

    /**
     * 构造拦截器。
     *
     * @param config 文档工具包配置（沙箱根目录/输出目录）
     */
    public DocumentPathInterceptor(DocToolConfig config) {
        this.config = config;
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public void before(String toolName, Object request) throws SecurityException {
        if (request == null) {
            return;
        }
        if (request instanceof DocConvertRequest convert) {
            validateSource(convert.source(), toolName);
        } else if (request instanceof TemplateSchemaRequest schema) {
            validatePath(schema.templatePath(), toolName);
        } else if (request instanceof RenderRequest render) {
            validateRender(render, toolName);
        }
    }

    /**
     * 校验文档来源（路径 / URL / Base64 三态）。
     *
     * @param source   来源字符串
     * @param toolName 工具名（用于异常信息）
     * @throws SecurityException 来源非法时抛出
     */
    private void validateSource(String source, String toolName) throws SecurityException {
        if (source == null || source.isBlank()) {
            throw new SecurityException("工具 [" + toolName + "] 参数校验失败: source 不能为空");
        }
        String lower = source.trim().toLowerCase();
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return; // URL 来源合法，下载落盘由 DocSourceResolver 处理
        }
        if (lower.startsWith("base64:")) {
            return; // Base64 来源合法，解码落盘由 DocSourceResolver 处理
        }
        // 本地路径：沙箱校验
        validatePath(source, toolName);
    }

    /**
     * 校验本地路径位于沙箱根目录内。
     *
     * @param userPath 用户提供的路径
     * @param toolName 工具名
     * @throws SecurityException 路径穿越时抛出
     */
    private void validatePath(String userPath, String toolName) throws SecurityException {
        try {
            Path resolved = PathSafety.resolveSafePath(config.effectiveBaseDir(), userPath);
            if (!PathSafety.isWithinBaseDir(config.effectiveBaseDir(), resolved)) {
                throw new SecurityException("工具 [" + toolName + "] 路径越界: " + userPath);
            }
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("工具 [" + toolName + "] 路径校验失败: " + userPath, e);
        }
    }

    /**
     * 校验渲染请求：模板路径沙箱 + 输出路径沙箱 + 禁止覆盖模板。
     *
     * @param render   渲染请求
     * @param toolName 工具名
     * @throws SecurityException 校验不通过时抛出
     */
    private void validateRender(RenderRequest render, String toolName) throws SecurityException {
        validatePath(render.templatePath(), toolName);

        if (render.outputPath() != null && !render.outputPath().isBlank()) {
            Path output = PathSafety.resolveSafePath(config.effectiveOutputDir(), render.outputPath());
            if (!PathSafety.isWithinBaseDir(config.effectiveOutputDir(), output)) {
                throw new SecurityException("工具 [" + toolName + "] 输出路径越界: " + render.outputPath());
            }
            Path template = PathSafety.resolveSafePath(config.effectiveBaseDir(), render.templatePath());
            if (output.toAbsolutePath().normalize().equals(template.toAbsolutePath().normalize())) {
                throw new SecurityException("工具 [" + toolName + "] 禁止覆盖输入模板: " + render.outputPath());
            }
        }
    }
}