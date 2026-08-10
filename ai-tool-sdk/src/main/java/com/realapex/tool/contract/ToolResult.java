package com.realapex.tool.contract;

import lombok.Builder;
import lombok.Data;

/**
 * 工具执行标准返回包装。
 * <p>统一封装工具执行的成功/失败状态、数据和耗时，
 * 便于 Agent SDK 和安全拦截器做标准化处理。</p>
 */
@Data
@Builder
public class ToolResult {

    /** 是否执行成功 */
    private boolean success;

    /** 执行返回数据（失败时为 null） */
    private Object data;

    /** 错误信息（成功时为 null） */
    private String error;

    /** 执行耗时（毫秒） */
    private long durationMs;

    /**
     * 创建成功结果。
     *
     * @param data       返回数据
     * @param durationMs 耗时毫秒
     * @return ToolResult
     */
    public static ToolResult ok(Object data, long durationMs) {
        return ToolResult.builder()
                .success(true)
                .data(data)
                .durationMs(durationMs)
                .build();
    }

    /**
     * 创建成功结果（无耗时）。
     */
    public static ToolResult ok(Object data) {
        return ok(data, 0);
    }

    /**
     * 创建失败结果。
     *
     * @param error      错误信息
     * @param durationMs 耗时毫秒
     * @return ToolResult
     */
    public static ToolResult fail(String error, long durationMs) {
        return ToolResult.builder()
                .success(false)
                .error(error)
                .durationMs(durationMs)
                .build();
    }

    /**
     * 创建失败结果（无耗时）。
     */
    public static ToolResult fail(String error) {
        return fail(error, 0);
    }
}
