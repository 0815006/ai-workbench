package com.realapex.client.trace;

import com.realapex.client.model.AiRequest;
import com.realapex.client.model.AiResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Trace 拦截器——在每次 LLM 调用生命周期内抓取 Prompt/Messages/ToolCalls/Tokens。
 * <p>配合 {@link AsyncLLMLogStorageService} 完成"预插入 RUNNING + 异步更新 SUCCESS/FAILED"</p>
 * 的全自动落盘。业务方无需手动处理状态机与耗时统计，仅需在入口处设置
 * {@link TraceContext}（见 {@link LLMTraceContext}）。</p>
 *
 * <h3>核心指标</h3>
 * <ul>
 *   <li><b>TTFT（首 Token 延迟）</b>：流式场景 {@link #onFirstToken} 测量首个内容到达耗时，落盘 {@code first_token_latency_ms}</li>
 *   <li><b>Latency（总耗时）</b>：从 {@code onStart} 到 {@code onEnd} 的毫秒差</li>
 *   <li><b>Token 统计</b>：由响应中的 {@code usage} 填充</li>
 * </ul>
 */
@Slf4j
public class LLMInvokeTraceInterceptor {

    private final AsyncLLMLogStorageService storageService;
    private final LLMTraceProperties properties;

    /**
     * 构造拦截器。
     *
     * @param storageService 异步落盘服务
     * @param properties     Trace 配置（用于截断上限等）
     */
    public LLMInvokeTraceInterceptor(AsyncLLMLogStorageService storageService,
                                     LLMTraceProperties properties) {
        this.storageService = storageService;
        this.properties = properties;
    }

    /**
     * 调用开始：预插入一条 {@code RUNNING} 记录，返回后续更新所需的 log_id。
     * <p>从当前线程的 {@link LLMTraceContext} 读取业务上下文；未设置时自动生成
     * {@code trace_id} 并用默认场景名，保证每次调用都有可追溯记录。</p>
     *
     * @param request 统一请求对象
     * @param callType 调用类型（如 STREAM / ASYNC_CHAT / TOOL_CALL）
     * @return 本次调用的 TraceSession（含 log_id、起始时间戳），未启用或失败时返回 null
     */
    public TraceSession onStart(AiRequest request, String callType) {
        TraceContext ctx = LLMTraceContext.get();
        TraceRecord record = TraceRecord.builder()
                .traceId(ctx != null && ctx.getTraceId() != null ? ctx.getTraceId() : LLMTraceContext.newTraceId())
                .parentLogId(ctx != null ? ctx.getParentLogId() : null)
                .sceneType(ctx != null && ctx.getSceneType() != null ? ctx.getSceneType() : "GENERIC")
                .sessionId(ctx != null ? ctx.getSessionId() : null)
                .subDirId(ctx != null ? ctx.getSubDirId() : null)
                .userId(ctx != null ? ctx.getUserId() : null)
                .provider(request != null && request.getModel() != null ? inferProvider(request.getModel()) : "unknown")
                .modelName(request != null ? request.getModel() : "unknown")
                .callType(callType)
                .requestPayload(TracePayloads.truncate(
                        TracePayloads.buildRequestPayload(request), properties.getMaxPayloadLength()))
                .build();
        String logId = storageService.saveStart(record);
        TraceSession session = new TraceSession();
        session.logId = logId;
        session.traceId = record.getTraceId();
        session.startNano = System.nanoTime();
        session.startedAt = OffsetDateTime.now();
        return logId == null ? null : session;
    }

    /**
     * 记录首个 Token 到达时间（TTFT）。流式场景下应在首个内容/推理增量到达时调用。
     *
     * @param session 当前调用会话
     */
    public void onFirstToken(TraceSession session) {
        if (session != null) {
            session.firstTokenNano = System.nanoTime();
        }
    }

    /**
     * 调用结束（成功）：异步更新为 {@code SUCCESS} 并附带响应载荷与 Token/耗时统计。
     *
     * @param session  调用会话（来自 {@link #onStart}）
     * @param response 完整响应对象（可能为 null）
     */
    public void onSuccess(TraceSession session, AiResponse response) {
        if (session == null || session.logId == null) {
            return;
        }
        TraceRecord end = TraceRecord.builder()
                .logId(session.logId)
                .status(TraceStatus.SUCCESS)
                .responsePayload(TracePayloads.truncate(
                        TracePayloads.buildResponsePayload(response), properties.getMaxPayloadLength()))
                .promptTokens(response != null && response.getUsage() != null ? response.getUsage().getPromptTokens() : 0)
                .completionTokens(response != null && response.getUsage() != null ? response.getUsage().getCompletionTokens() : 0)
                .totalTokens(response != null && response.getUsage() != null ? response.getUsage().getTotalTokens() : 0)
                .latencyMs(millis(session.startNano))
                .firstTokenLatencyMs(session.firstTokenNano > 0 ? millis(session.firstTokenNano - session.startNano) : 0)
                .build();
        storageService.saveEnd(end);
    }

    /**
     * 调用结束（失败）：异步更新为 {@code FAILED} 并记录错误堆栈。
     *
     * @param session 调用会话
     * @param error   异常对象
     */
    public void onFailure(TraceSession session, Throwable error) {
        if (session == null || session.logId == null) {
            return;
        }
        StringWriter sw = new StringWriter();
        if (error != null) {
            error.printStackTrace(new PrintWriter(sw));
        }
        String stack = sw.toString();
        TraceRecord end = TraceRecord.builder()
                .logId(session.logId)
                .status(TraceStatus.FAILED)
                .errorMessage(stack.toString())
                .latencyMs(millis(session.startNano))
                .build();
        storageService.saveEnd(end);
    }

    /**
     * 从模型名推断 provider（用于 provider 列）。
     *
     * @param modelName 模型名
     * @return provider 名（openai/deepseek/ollama/unknown）
     */
    private String inferProvider(String modelName) {
        String lower = modelName.toLowerCase();
        if (lower.contains("deepseek")) {
            return "deepseek";
        }
        if (lower.contains("gpt") || lower.contains("o3") || lower.contains("o1")) {
            return "openai";
        }
        if (lower.contains("qwen")) {
            return "qwen";
        }
        if (lower.contains("llama")) {
            return "ollama";
        }
        return "openai";
    }

    private long millis(long nano) {
        return (System.nanoTime() - nano) / 1_000_000L;
    }

    /**
     * 单次 LLM 调用的 Trace 会话，保存调用期间的中间状态。
     * <p>非线程共享，由单次调用内串行使用。</p>
     */
    public static class TraceSession {
        /** 数据库 log_id（更新时定位记录） */
        public String logId;

        /** 全局 trace_id */
        public String traceId;

        /** 调用开始纳秒时间戳 */
        public long startNano;

        /** 调用开始 UTC 时间 */
        public OffsetDateTime startedAt;

        /** 首个 Token 到达纳秒时间戳（0 表示从未到达） */
        public long firstTokenNano;

        /** UTC 起始时间毫秒值（用于数据库 start_time 计算） */
        public java.time.OffsetDateTime startTime() {
            return startedAt.withOffsetSameInstant(ZoneOffset.UTC);
        }
    }
}