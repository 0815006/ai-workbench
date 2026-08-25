package com.realapex.tool.contract;

/**
 * 工具统一契约接口——所有 AI 可调用工具的顶层抽象。
 * <p>实现此接口的工具可被 {@code ai-agent-sdk} 的 AgentRunner 自动发现、
 * Schema 生成和 ReAct 循环驱动，也可独立被应用层直接调用。</p>
 *
 * <h3>实现示例</h3>
 * <pre>{@code
 * public class CalculatorTool implements AgentTool<CalculatorTool.Input, Double> {
 *     &#64;Override public String name() { return "calculator"; }
 *     &#64;Override public String description() { return "执行数学表达式计算"; }
 *     &#64;Override public Class<Input> requestClass() { return Input.class; }
 *
 *     &#64;Override
 *     public Double execute(Input req) {
 *         return eval(req.expression());
 *     }
 *
 *     public record Input(
 *         &#64;ToolParam(description = "数学表达式", required = true)
 *         String expression
 *     ) {}
 * }
 * }</pre>
 *
 * @param <REQ>  工具请求参数类型（建议使用 Java Record，配合 @ToolParam 注解）
 * @param <RESP> 工具返回结果类型
 */
public interface AgentTool<REQ, RESP> {

    /**
     * 工具名称（对应 OpenAI Function Calling 的 function name）。
     * <p>命名建议：snake_case，如 "get_explain_plan"、"query_metrics"。</p>
     *
     * @return 工具唯一名称
     */
    String name();

    /**
     * 工具描述（供大模型理解工具用途与调用时机）。
     * <p>描述越具体，大模型越能准确决策是否调用。</p>
     *
     * @return 工具描述文本
     */
    String description();

    /**
     * 请求参数类型。
     * <p>SDK 通过反射此类型的字段信息自动生成 JSON Schema。</p>
     *
     * @return 请求参数 Class
     */
    Class<REQ> requestClass();

    /**
     * 执行工具逻辑。
     * <p>当大模型决定调用此工具时，SDK 将 LLM 输出的 JSON 参数反序列化为 REQ 实例并传入。</p>
     *
     * @param request 反序列化后的请求参数
     * @return 工具执行结果
     * @throws Exception 执行失败时抛出
     */
    RESP execute(REQ request) throws Exception;

    /**
     * 该工具是否需要人工审批（HITL）。
     * <p>返回 true 时，AgentRunner 在 LLM 拟调用此工具前中断 ReAct 循环，
     * 抛出 {@code AgentSuspendedException} 等待人工审批，审批通过后恢复执行。</p>
     * <p>默认返回 false（无需审批）。实现类可覆盖，或通过
     * {@code @Tool(requiresApproval = true)} 注解声明。</p>
     *
     * @return true 表示需要人工审批
     */
    default boolean requiresApproval() {
        return false;
    }

    /**
     * 安全执行工具，将异常包装为 {@link ToolResult}。
     * <p>默认实现调用 {@link #execute} 并包装结果。子类可覆盖以自定义错误处理。</p>
     *
     * @param request 反序列化后的请求参数
     * @return 标准 ToolResult
     */
    @SuppressWarnings("unchecked")
    default ToolResult executeSafely(REQ request) {
        long start = System.currentTimeMillis();
        try {
            Object result = execute(request);
            return ToolResult.ok(result, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return ToolResult.fail(e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
