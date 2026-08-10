package com.realapex.tool.annotation;

import java.lang.annotation.*;

/**
 * 工具参数描述注解——用于标注 {@link AgentTool} 的请求 Record 字段。
 * <p>SDK 的 {@code SchemaGenerator} 会读取此注解自动生成更精确的 JSON Schema，
 * 安全拦截器会据此做参数校验。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public record QueryRequest(
 *     &#64;ToolParam(description = "待执行的只读 SQL 语句", required = true)
 *     String sql,
 *
 *     &#64;ToolParam(description = "最大返回行数", required = false)
 *     Integer maxRows
 * ) {}
 * }</pre>
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolParam {

    /**
     * 参数描述（供大模型理解参数含义）。
     *
     * @return 参数描述文本
     */
    String description() default "";

    /**
     * 是否必填。
     *
     * @return true 表示必填
     */
    boolean required() default true;
}
