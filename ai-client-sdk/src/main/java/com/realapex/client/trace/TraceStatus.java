package com.realapex.client.trace;

/**
 * LLM 调用日志生命周期状态机枚举。
 * <p>对应 {@code sys_llm_invoke_log.status} 列的 5 种状态，反映一次调用
 * 从创建到完成的完整异步生命周期：</p>
 * <ul>
 *   <li>{@link #INIT} — 日志记录已创建（预留），尚未开始</li>
 *   <li>{@link #RUNNING} — 正在调用大模型 / 生成 / 思考</li>
 *   <li>{@link #STREAMING} — SSE 流式传输中（逐字落盘、首 Token 已到）</li>
 *   <li>{@link #SUCCESS} — 正常结束</li>
 *   <li>{@link #FAILED} — 异常 / 超时结束</li>
 * </ul>
 */
public enum TraceStatus {

    /** 已创建 */
    INIT("INIT"),

    /** 运行中：模型生成 / 思考中 */
    RUNNING("RUNNING"),

    /** 流式传输中 */
    STREAMING("STREAMING"),

    /** 完成 */
    SUCCESS("SUCCESS"),

    /** 失败 / 超时 */
    FAILED("FAILED");

    private final String code;

    TraceStatus(String code) {
        this.code = code;
    }

    /**
     * 获取数据库存储的枚举值。
     *
     * @return 存储字符串
     */
    public String code() {
        return code;
    }
}