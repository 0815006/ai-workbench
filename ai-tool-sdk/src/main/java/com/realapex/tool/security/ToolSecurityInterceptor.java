package com.realapex.tool.security;

/**
 * 工具安全拦截器——在工具执行前后进行安全校验和审计。
 * <p>多个拦截器可按优先级链式组合。任何拦截器的 {@link #before} 抛出
 * {@link SecurityException} 将阻止工具执行。</p>
 *
 * <h3>内置实现</h3>
 * <ul>
 *   <li>{@link ParamValidator} — 参数有效性校验</li>
 *   <li>{@link TimeoutInterceptor} — 执行超时控制</li>
 *   <li>{@link DangerousCommandFilter} — 高危指令过滤</li>
 * </ul>
 */
public interface ToolSecurityInterceptor {

    /**
     * 工具执行前回调。
     * <p>可在此做参数校验、权限鉴权、敏感词过滤。
     * 抛出 {@link SecurityException} 将阻止工具执行。</p>
     *
     * @param toolName 工具名称
     * @param request  工具请求参数
     * @throws SecurityException 校验不通过时抛出
     */
    default void before(String toolName, Object request) throws SecurityException {
    }

    /**
     * 工具执行后回调（成功时）。
     * <p>可在此做结果审计、日志记录。</p>
     *
     * @param toolName 工具名称
     * @param request  工具请求参数
     * @param result   工具返回结果
     */
    default void after(String toolName, Object request, Object result) {
    }

    /**
     * 工具执行异常回调。
     *
     * @param toolName 工具名称
     * @param request  工具请求参数
     * @param error    异常信息
     */
    default void onError(String toolName, Object request, Throwable error) {
    }

    /**
     * 拦截器优先级（数值越小越先执行）。
     *
     * @return 优先级，默认 100
     */
    default int priority() {
        return 100;
    }
}
