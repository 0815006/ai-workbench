package com.realapex.tool.annotation;

import java.lang.annotation.*;

/**
 * 标记一个类或方法为 AI 可调用的工具。
 * <p>标注在 Spring Bean 的 public 方法上，SDK 启动时自动扫描并注册到 {@code ToolRegistry}。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 方式 A：标注在方法上
 * &#64;Component
 * public class MonitoringTools {
 *     &#64;Tool(description = "根据指标名称查询 Prometheus 近 10 分钟打点")
 *     public String queryMetric(String metricName) {
 *         return prometheusClient.query(metricName);
 *     }
 * }
 *
 * // 方式 B：标注在类上（实现 AgentTool 接口）
 * &#64;Tool(description = "执行数学表达式计算", readOnly = true)
 * public class CalculatorTool implements AgentTool<Input, Double> { ... }
 * }</pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Tool {

    /**
     * 工具名称（对应 OpenAI Function name）。
     * <p>留空时默认使用：方法名 / 类名转 snake_case。</p>
     *
     * @return 工具名称
     */
    String name() default "";

    /**
     * 工具描述（供大模型理解用途）。
     * <p>必须提供，否则大模型无法正确决策是否调用。</p>
     *
     * @return 工具描述
     */
    String description();

    /**
     * 是否只读工具。
     * <p>只读工具不修改系统状态，安全校验级别较低。
     * 非只读工具会触发更严格的安全拦截。</p>
     *
     * @return true 表示只读
     */
    boolean readOnly() default false;
}
