package com.realapex.client.trace;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 一条 LLM 调用日志记录，对应 {@code sys_llm_invoke_log} 表的一行。
 * <p>由 {@link LLMInvokeTraceInterceptor} 在每次调用过程中构建，并交由
 * {@link AsyncLLMLogStorageService} 异步落盘。字段与 DDL 列一一对应，
 * 包含异步追踪标识、业务上下文、生命周期状态、Token 统计与 JSONB 载荷。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TraceRecord {

    /** 主键 Log ID（插入时为 null，由数据库生成） */
    private String logId;

    /** 全局 Trace ID（跨线程/异步任务传递） */
    private String traceId;

    /** 父 Log ID（Agent 拆解子任务/多轮 Prompt 树状追踪） */
    private String parentLogId;

    /** 场景类型：DB_ANALYSIS, FILE_ANALYSIS ... */
    private String sceneType;

    /** 会话 ID */
    private String sessionId;

    /** 隔离目录 ID（针对文件场景） */
    private String subDirId;

    /** 用户 ID */
    private String userId;

    /** 生命周期状态：INIT -> RUNNING -> STREAMING -> SUCCESS/FAILED */
    private TraceStatus status;

    /** 模型提供商：openai, deepseek, ollama ... */
    private String provider;

    /** 模型名称：deepseek-chat, gpt-4o ... */
    private String modelName;

    /** 调用类型：ASYNC_CHAT, STREAM, EMBEDDING, TOOL_CALL */
    private String callType;

    /** 提示词 Token 数 */
    private int promptTokens;

    /** 生成 Token 数 */
    private int completionTokens;

    /** 总 Token 数 */
    private int totalTokens;

    /** 总耗时 (ms) */
    private long latencyMs;

    /** 首 Token 延迟 TTFT (ms)，流式打字机场景）
    */
    private long firstTokenLatencyMs;

    /** 请求载荷 JSON（System Prompt, Messages, Tools），落盘前截断 */
    private String requestPayload;

    /** 响应载荷 JSON（Model Reply, Reasoning, ToolCalls 结果），落盘前截断 */
    private String responsePayload;

    /** 错误堆栈文本 */
    private String errorMessage;

    /** 创建时间 */
    private OffsetDateTime createdAt;

    /** 异步开始处理时间 */
    private OffsetDateTime startTime;

    /** 异步结束时间 */
    private OffsetDateTime endTime;
}