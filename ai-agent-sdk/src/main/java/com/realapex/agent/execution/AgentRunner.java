package com.realapex.agent.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realapex.agent.context.ContextTrimmer;
import com.realapex.agent.event.AgentEventListener;
import com.realapex.agent.event.AgentStreamEvent;
import com.realapex.agent.event.AgentStreamListener;
import com.realapex.agent.exception.AgentSuspendedException;
import com.realapex.agent.tool.ToolRegistry;
import com.realapex.client.client.AiClient;
import com.realapex.client.client.StreamListener;
import com.realapex.client.model.AiRequest;
import com.realapex.client.model.AiResponse;
import com.realapex.client.model.Message;
import com.realapex.client.model.ToolCall;
import com.realapex.client.model.ToolDefinition;
import com.realapex.client.model.Usage;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.schema.SchemaGenerator;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * ReAct (Thought-Action-Observation) 循环驱动器。
 * <p>这是 ai-agent-sdk 的核心引擎，负责驱动大模型在 while 循环中
 * 自主决策、调用工具、观察结果，直到任务完成或达到步数上限。</p>
 *
 * <h3>线程安全</h3>
 * <p>本实例是线程安全的单例。所有多轮对话状态限定在单次 {@link #run(AgentRequest)}
 * 调用作用域（局部变量），不共享任何跨请求的可变状态。
 * 唯一的共享状态是 HITL 挂起快照表 {@code suspendedStates}（ConcurrentHashMap）。</p>
 *
 * <h3>ReAct 循环流程</h3>
 * <ol>
 *   <li>构建初始消息列表（system + user + 附加上下文）</li>
 *   <li>调用 LLM → 解析响应</li>
 *   <li>若无 tool_calls → 返回最终文本</li>
 *   <li>若有 tool_calls → 检查 HITL：存在 requiresApproval 工具则中断挂起</li>
 *   <li>虚拟线程并行执行所有工具</li>
 *   <li>将工具结果拼装为 assistant(tool_calls) + tool_result 消息追加到历史</li>
 *   <li>上下文裁剪检查 → 回到步骤 2</li>
 * </ol>
 *
 * <h3>流式模式（默认启用）</h3>
 * <p>当 {@link AgentRequest#getStream()} 为 true 时，通过
 * {@link AiClient#generate(AiRequest, StreamListener)} 走 SSE 流式路径——
 * 底层逐字推送 {@link AgentEventListener#onChunk(String)}，同时内部累积
 * 文本和工具调用，最终仍返回完整的 {@link AiResponse}（含 tool_calls、usage）。</p>
 *
 * <h3>HITL 人工审批（Human-in-the-Loop）</h3>
 * <p>当 LLM 拟调用 {@code requiresApproval=true} 的高危工具时，AgentRunner 中断循环，
 * 抛出 {@link AgentSuspendedException}（携带 {@link AgentState} 快照）。
 * 外部系统持久化快照并等待人工审批，审批后调用 {@link #resume(String, ApprovalResult)} 恢复执行。</p>
 */
@Slf4j
public class AgentRunner {

    private final AiClient aiClient;
    private final SchemaGenerator schemaGenerator;
    private final ObjectMapper objectMapper;
    private final ExecutorService toolExecutor;
    private final ContextTrimmer contextTrimmer;

    /** HITL 挂起快照表：suspendId → AgentState（线程安全） */
    private final Map<String, AgentState> suspendedStates = new ConcurrentHashMap<>();
    private final AtomicLong suspendIdSeq = new AtomicLong(0);

    /**
     * 创建 AgentRunner 实例。
     *
     * @param aiClient        ai-client-sdk 客户端实例（用于 LLM 通信）
     * @param schemaGenerator JSON Schema 生成器
     */
    public AgentRunner(AiClient aiClient, SchemaGenerator schemaGenerator) {
        this.aiClient = aiClient;
        this.schemaGenerator = schemaGenerator;
        this.objectMapper = new ObjectMapper();
        this.toolExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.contextTrimmer = new ContextTrimmer();
    }

    /**
     * 执行 Agent，返回最终文本结果。
     * <p>达到 maxSteps 时不再抛出异常，而是通过
     * {@link AgentResult#isMaxStepsExceeded()} 标记超限状态。</p>
     *
     * @param request Agent 请求配置
     * @return Agent 执行结果
     * @throws AgentSuspendedException 当 LLM 拟调用需要人工审批的工具时抛出
     */
    public AgentResult run(AgentRequest request) {
        return doRun(request, null);
    }

    /**
     * 执行 Agent，并将最终文本映射为指定类型的结构化对象。
     * <p>当 Agent 完成 ReAct 循环后，SDK 在最后一步自动调用
     * {@link AiClient#generateObject} 将 LLM 最终输出映射为强类型 Java 对象。</p>
     *
     * @param request     Agent 请求配置
     * @param outputClass 目标输出类型
     * @param <T>         目标类型
     * @return Agent 执行结果（result.structuredOutput 包含强类型对象）
     * @throws AgentSuspendedException 当 LLM 拟调用需要人工审批的工具时抛出
     */
    @SuppressWarnings("unchecked")
    public <T> AgentResult run(AgentRequest request, Class<T> outputClass) {
        return doRun(request, outputClass);
    }

    /**
     * 恢复一个被 HITL 挂起的 Agent 执行。
     * <p>人工审批完成后调用本方法，根据 {@link ApprovalResult} 决定待审批工具的执行策略：</p>
     * <ul>
     *   <li><b>批准</b>：执行待审批工具，将结果回传 LLM 继续 ReAct 循环</li>
     *   <li><b>拒绝</b>：将拒绝原因作为 toolResult 回传 LLM，驱动 LLM 调整方案</li>
     * </ul>
     *
     * @param suspendId      挂起唯一 ID（来自 {@link AgentSuspendedException#getSuspendId()}）
     * @param approvalResult 人工审批结果
     * @return Agent 执行结果
     * @throws IllegalArgumentException suspendId 不存在或已被恢复时抛出
     */
    public AgentResult resume(String suspendId, ApprovalResult approvalResult) {
        AgentState state = suspendedStates.remove(suspendId);
        if (state == null) {
            throw new IllegalArgumentException("suspendId 不存在或已被恢复: " + suspendId);
        }
        log.info("恢复挂起 Agent: suspendId={}, approved={}, operator={}",
                suspendId, approvalResult.approved(), approvalResult.operator());
        return doResume(state, approvalResult);
    }

    /**
     * 查询当前挂起的 Agent 状态快照（不消费）。
     *
     * @param suspendId 挂起唯一 ID
     * @return AgentState 快照，不存在返回 null
     */
    public AgentState getSuspendedState(String suspendId) {
        return suspendedStates.get(suspendId);
    }

    /**
     * 当前挂起的 Agent 数量。
     *
     * @return 挂起数量
     */
    public int suspendedCount() {
        return suspendedStates.size();
    }

    // ==================== 核心 ReAct 循环 ====================

    @SuppressWarnings("unchecked")
    private <T> AgentResult doRun(AgentRequest request, Class<T> outputClass) {
        long startTime = System.currentTimeMillis();
        AgentEventListener listener = request.getListener();
        AgentStreamListener streamListener = request.getStreamListener();
        int maxSteps = Math.max(1, request.getMaxSteps());
        boolean streaming = Boolean.TRUE.equals(request.getStream());

        // 构建工具注册表 + ToolDefinition 列表
        ToolRegistry registry = buildRegistry(request.getTools());
        List<ToolDefinition> toolDefs = registry.isEmpty() ? List.of() : registry.getToolDefinitions();

        // 构建初始消息列表
        List<Message> messages = buildInitialMessages(request);

        // 累计 Token 统计
        int totalPromptTokens = 0;
        int totalCompletionTokens = 0;
        int totalTokens = 0;
        List<AgentStepResult> stepResults = new ArrayList<>();

        int step = 0;
        String finalText = null;
        Object structuredOutput = null;
        boolean maxStepsExceeded = false;

        while (step < maxSteps) {
            step++;
            long stepStart = System.currentTimeMillis();

            // === 上下文裁剪 ===
            messages = contextTrimmer.trim(messages);

            // === 事件：onStepStart ===
            if (listener != null) {
                listener.onStepStart(step);
            }

            // === 构建 AiRequest ===
            AiRequest aiReq = buildAiRequest(messages, toolDefs, request);

            // === 调用 LLM ===
            AiResponse aiResponse = callLlm(aiReq, streaming, listener, streamListener);

            // === 提取响应信息 ===
            Usage stepUsage = aiResponse.getUsage();
            String stepText = aiResponse.getText();
            List<ToolCall> toolCalls = aiResponse.hasToolCalls()
                    ? aiResponse.getToolCalls() : List.of();

            // === 累计 Usage ===
            if (stepUsage != null) {
                totalPromptTokens += stepUsage.getPromptTokens();
                totalCompletionTokens += stepUsage.getCompletionTokens();
                totalTokens += stepUsage.getTotalTokens();
            }

            // === 判断终止条件：无工具调用 → LLM 给出最终回答 ===
            if (toolCalls.isEmpty()) {
                finalText = stepText;

                AgentStepResult stepResult = AgentStepResult.builder()
                        .stepNumber(step)
                        .llmResponse(aiResponse)
                        .toolCalls(List.of())
                        .toolResults(Map.of())
                        .usage(stepUsage)
                        .durationMs(System.currentTimeMillis() - stepStart)
                        .build();
                stepResults.add(stepResult);

                if (listener != null) {
                    listener.onStepFinish(stepResult);
                }
                break;
            }

            // === HITL 检查：是否存在需要人工审批的工具 ===
            List<ToolCall> pendingApproval = toolCalls.stream()
                    .filter(tc -> {
                        AgentTool<?, ?> tool = registry.get(tc.getName());
                        return tool != null && tool.requiresApproval();
                    })
                    .collect(Collectors.toList());

            if (!pendingApproval.isEmpty()) {
                AgentState state = buildSuspendedState(request, step, maxSteps, messages,
                        toolCalls, totalPromptTokens, totalCompletionTokens, totalTokens,
                        stepResults, registry);
                suspendedStates.put(state.getSuspendId(), state);

                // 流式推送：工具触发事件
                if (streamListener != null) {
                    for (ToolCall tc : pendingApproval) {
                        streamListener.onToolCallStart(tc.getId(), tc.getName(), tc.getArguments());
                    }
                }
                log.warn("Agent 挂起等待人工审批: suspendId={}, 待审批工具={}",
                        state.getSuspendId(),
                        pendingApproval.stream().map(ToolCall::getName).collect(Collectors.joining(", ")));
                throw new AgentSuspendedException(state);
            }

            // === 并行执行工具 ===
            Map<String, Object> toolResults = executeToolsInParallel(toolCalls, registry, listener, streamListener);

            // === 将 assistant(tool_calls) + tool_results 追加到消息历史 ===
            appendToolMessages(messages, toolCalls, toolResults);

            // === 记录步骤结果 ===
            AgentStepResult stepResult = AgentStepResult.builder()
                    .stepNumber(step)
                    .llmResponse(aiResponse)
                    .toolCalls(toolCalls)
                    .toolResults(toolResults)
                    .usage(stepUsage)
                    .durationMs(System.currentTimeMillis() - stepStart)
                    .build();
            stepResults.add(stepResult);

            if (listener != null) {
                listener.onStepFinish(stepResult);
            }

            log.debug("Step {} 完成: {} 个工具调用, {} tokens",
                    step, toolCalls.size(),
                    stepUsage != null ? stepUsage.getTotalTokens() : "N/A");
        }

        // === 达到 maxSteps 仍未完成 ===
        if (step >= maxSteps && finalText == null && structuredOutput == null) {
            maxStepsExceeded = true;
            if (!stepResults.isEmpty()) {
                AgentStepResult lastStep = stepResults.get(stepResults.size() - 1);
                if (lastStep.getLlmResponse() != null) {
                    finalText = lastStep.getLlmResponse().getText();
                }
            }
            if (finalText == null || finalText.isBlank()) {
                finalText = "[Agent 在 " + maxSteps + " 步内未完成最终回答]";
            }
        }

        // === 结构化输出（如果指定了 outputClass） ===
        if (outputClass != null && finalText != null && !finalText.isBlank()) {
            structuredOutput = extractStructuredOutput(finalText, outputClass, request);
        }

        // === 构建 Usage ===
        Usage totalUsage = Usage.builder()
                .promptTokens(totalPromptTokens)
                .completionTokens(totalCompletionTokens)
                .totalTokens(totalTokens)
                .build();

        // === 构建 AgentResult ===
        AgentResult result = AgentResult.builder()
                .finalText(finalText)
                .structuredOutput(structuredOutput)
                .totalSteps(step)
                .totalUsage(totalUsage)
                .totalDurationMs(System.currentTimeMillis() - startTime)
                .stepResults(stepResults)
                .maxStepsExceeded(maxStepsExceeded)
                .build();

        // === 事件：onComplete + 流式 FinalResult ===
        if (listener != null) {
            listener.onComplete(result);
        }
        if (streamListener != null) {
            streamListener.onFinalResult(finalText, structuredOutput);
        }

        // === 熔断（不再抛异常，避免与 onComplete 冲突） ===
        if (maxStepsExceeded) {
            log.warn("Agent 达到最大步数限制 ({}), 未能在限制步数内完成最终回答", maxSteps);
        }

        return result;
    }

    /**
     * 恢复挂起 Agent 的 ReAct 循环。
     */
    @SuppressWarnings("unchecked")
    private <T> AgentResult doResume(AgentState state, ApprovalResult approvalResult) {
        long startTime = System.currentTimeMillis();
        AgentEventListener listener = state.getListener();
        AgentStreamListener streamListener = state.getStreamListener();
        int maxSteps = state.getMaxSteps();
        boolean streaming = Boolean.TRUE.equals(state.getStream());

        // 重建工具注册表
        ToolRegistry registry = buildRegistry(state.getTools());
        List<ToolDefinition> toolDefs = registry.isEmpty() ? List.of() : registry.getToolDefinitions();

        // 恢复消息历史与累计统计
        List<Message> messages = new ArrayList<>(state.getMessages());
        List<AgentStepResult> stepResults = new ArrayList<>(state.getStepResults());
        int totalPromptTokens = state.getTotalPromptTokens();
        int totalCompletionTokens = state.getTotalCompletionTokens();
        int totalTokens = state.getTotalTokens();
        int step = state.getStep();

        // === 处理待审批工具调用 ===
        List<ToolCall> pendingToolCalls = state.getPendingToolCalls();
        Map<String, Object> toolResults = new LinkedHashMap<>();

        for (ToolCall tc : pendingToolCalls) {
            if (approvalResult.approved()) {
                // 批准：执行工具
                Object result = executeSingleTool(tc, registry, listener, streamListener);
                toolResults.put(tc.getId(), result);
            } else {
                // 拒绝：将拒绝原因作为 toolResult 回传 LLM
                String rejectMsg = "工具 [" + tc.getName() + "] 已被人工拒绝执行。"
                        + "审批人: " + approvalResult.operator()
                        + ", 拒绝原因: " + (approvalResult.comment() == null ? "未说明" : approvalResult.comment())
                        + "。请调整执行方案，不要再次调用该工具。";
                toolResults.put(tc.getId(), rejectMsg);
                if (streamListener != null) {
                    streamListener.onToolCallResult(tc.getId(), tc.getName(), rejectMsg);
                }
            }
        }

        // === 追加 assistant(tool_calls) + tool_result 消息 ===
        appendToolMessages(messages, pendingToolCalls, toolResults);

        // === 记录恢复步骤 ===
        AgentStepResult resumeStep = AgentStepResult.builder()
                .stepNumber(step)
                .llmResponse(null)
                .toolCalls(pendingToolCalls)
                .toolResults(toolResults)
                .usage(null)
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
        stepResults.add(resumeStep);
        if (listener != null) {
            listener.onStepFinish(resumeStep);
        }

        // === 继续 ReAct 循环 ===
        String finalText = null;
        Object structuredOutput = null;
        boolean maxStepsExceeded = false;

        while (step < maxSteps) {
            step++;
            long stepStart = System.currentTimeMillis();

            messages = contextTrimmer.trim(messages);

            if (listener != null) {
                listener.onStepStart(step);
            }

            AiRequest aiReq = buildAiRequest(messages, toolDefs, state);
            AiResponse aiResponse = callLlm(aiReq, streaming, listener, streamListener);

            Usage stepUsage = aiResponse.getUsage();
            String stepText = aiResponse.getText();
            List<ToolCall> toolCalls = aiResponse.hasToolCalls()
                    ? aiResponse.getToolCalls() : List.of();

            if (stepUsage != null) {
                totalPromptTokens += stepUsage.getPromptTokens();
                totalCompletionTokens += stepUsage.getCompletionTokens();
                totalTokens += stepUsage.getTotalTokens();
            }

            if (toolCalls.isEmpty()) {
                finalText = stepText;
                AgentStepResult stepResult = AgentStepResult.builder()
                        .stepNumber(step)
                        .llmResponse(aiResponse)
                        .toolCalls(List.of())
                        .toolResults(Map.of())
                        .usage(stepUsage)
                        .durationMs(System.currentTimeMillis() - stepStart)
                        .build();
                stepResults.add(stepResult);
                if (listener != null) {
                    listener.onStepFinish(stepResult);
                }
                break;
            }

            // === HITL 检查（恢复后仍可能再次触发） ===
            List<ToolCall> pendingApproval = toolCalls.stream()
                    .filter(tc -> {
                        AgentTool<?, ?> tool = registry.get(tc.getName());
                        return tool != null && tool.requiresApproval();
                    })
                    .collect(Collectors.toList());

            if (!pendingApproval.isEmpty()) {
                AgentState newState = buildSuspendedState(state, step, maxSteps, messages,
                        toolCalls, totalPromptTokens, totalCompletionTokens, totalTokens,
                        stepResults, registry);
                suspendedStates.put(newState.getSuspendId(), newState);
                if (streamListener != null) {
                    for (ToolCall tc : pendingApproval) {
                        streamListener.onToolCallStart(tc.getId(), tc.getName(), tc.getArguments());
                    }
                }
                throw new AgentSuspendedException(newState);
            }

            Map<String, Object> toolResults2 = executeToolsInParallel(toolCalls, registry, listener, streamListener);
            appendToolMessages(messages, toolCalls, toolResults2);

            AgentStepResult stepResult = AgentStepResult.builder()
                    .stepNumber(step)
                    .llmResponse(aiResponse)
                    .toolCalls(toolCalls)
                    .toolResults(toolResults2)
                    .usage(stepUsage)
                    .durationMs(System.currentTimeMillis() - stepStart)
                    .build();
            stepResults.add(stepResult);
            if (listener != null) {
                listener.onStepFinish(stepResult);
            }
        }

        if (step >= maxSteps && finalText == null && structuredOutput == null) {
            maxStepsExceeded = true;
            if (!stepResults.isEmpty()) {
                AgentStepResult lastStep = stepResults.get(stepResults.size() - 1);
                if (lastStep.getLlmResponse() != null) {
                    finalText = lastStep.getLlmResponse().getText();
                }
            }
            if (finalText == null || finalText.isBlank()) {
                finalText = "[Agent 在 " + maxSteps + " 步内未完成最终回答]";
            }
        }

        if (state.getOutputClass() != null && finalText != null && !finalText.isBlank()) {
            structuredOutput = extractStructuredOutput(finalText, state.getOutputClass(), state);
        }

        Usage totalUsage = Usage.builder()
                .promptTokens(totalPromptTokens)
                .completionTokens(totalCompletionTokens)
                .totalTokens(totalTokens)
                .build();

        AgentResult result = AgentResult.builder()
                .finalText(finalText)
                .structuredOutput(structuredOutput)
                .totalSteps(step)
                .totalUsage(totalUsage)
                .totalDurationMs(System.currentTimeMillis() - startTime)
                .stepResults(stepResults)
                .maxStepsExceeded(maxStepsExceeded)
                .build();

        if (listener != null) {
            listener.onComplete(result);
        }
        if (streamListener != null) {
            streamListener.onFinalResult(finalText, structuredOutput);
        }

        return result;
    }

    // ==================== 请求构建 ====================

    /**
     * 构建 AiRequest，合并 AgentRequest 中的模型/温度配置。
     */
    private AiRequest buildAiRequest(List<Message> messages, List<ToolDefinition> toolDefs,
                                     AgentRequest request) {
        var builder = AiRequest.builder()
                .messages(new ArrayList<>(messages));

        if (!toolDefs.isEmpty()) {
            builder.tools(toolDefs);
        }
        if (request.getModel() != null && !request.getModel().isBlank()) {
            builder.model(request.getModel());
        }
        if (request.getTemperature() != null) {
            builder.temperature(request.getTemperature());
        }

        return builder.build();
    }

    /**
     * 构建 AiRequest（恢复场景，从 AgentState 取值）。
     */
    private AiRequest buildAiRequest(List<Message> messages, List<ToolDefinition> toolDefs,
                                     AgentState state) {
        var builder = AiRequest.builder()
                .messages(new ArrayList<>(messages));

        if (!toolDefs.isEmpty()) {
            builder.tools(toolDefs);
        }
        if (state.getModel() != null && !state.getModel().isBlank()) {
            builder.model(state.getModel());
        }
        if (state.getTemperature() != null) {
            builder.temperature(state.getTemperature());
        }

        return builder.build();
    }

    // ==================== 内部方法 ====================

    /**
     * 调用 LLM（流式/非流式统一入口）。
     */
    private AiResponse callLlm(AiRequest aiReq, boolean streaming,
                               AgentEventListener listener, AgentStreamListener streamListener) {
        if (streaming) {
            return aiClient.generate(aiReq, new StreamListener() {
                @Override
                public void onChunk(String chunk) {
                    if (listener != null) {
                        listener.onChunk(chunk);
                    }
                    if (streamListener != null) {
                        streamListener.onThoughtChunk(chunk);
                    }
                }

                @Override
                public void onComplete() {
                    // AiResponse 由 generate() 返回后统一处理
                }

                @Override
                public void onError(Throwable e) {
                    log.error("Agent 流式调用异常", e);
                }
            });
        }
        return aiClient.generate(aiReq);
    }

    /**
     * 构建初始消息列表。
     */
    private List<Message> buildInitialMessages(AgentRequest request) {
        List<Message> messages = new ArrayList<>();

        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(Message.system(request.getSystemPrompt()));
        }

        if (request.getMessages() != null) {
            messages.addAll(request.getMessages());
        }

        if (request.getUserPrompt() != null && !request.getUserPrompt().isBlank()) {
            messages.add(Message.user(request.getUserPrompt()));
        }

        return messages;
    }

    /**
     * 根据工具列表构建临时注册表。
     */
    private ToolRegistry buildRegistry(List<AgentTool<?, ?>> tools) {
        ToolRegistry registry = new ToolRegistry(schemaGenerator);
        if (tools != null) {
            for (AgentTool<?, ?> tool : tools) {
                registry.register(tool);
            }
        }
        return registry;
    }

    /**
     * 构建 HITL 挂起状态快照。
     */
    private AgentState buildSuspendedState(AgentRequest request, int step, int maxSteps,
                                           List<Message> messages, List<ToolCall> toolCalls,
                                           int totalPromptTokens, int totalCompletionTokens,
                                           int totalTokens, List<AgentStepResult> stepResults,
                                           ToolRegistry registry) {
        return AgentState.builder()
                .suspendId("suspend-" + suspendIdSeq.incrementAndGet())
                .step(step)
                .maxSteps(maxSteps)
                .messages(new ArrayList<>(messages))
                .pendingToolCalls(new ArrayList<>(toolCalls))
                .totalPromptTokens(totalPromptTokens)
                .totalCompletionTokens(totalCompletionTokens)
                .totalTokens(totalTokens)
                .stepResults(new ArrayList<>(stepResults))
                .tools(request.getTools())
                .listener(request.getListener())
                .streamListener(request.getStreamListener())
                .model(request.getModel())
                .temperature(request.getTemperature())
                .stream(request.getStream())
                .outputClass(request.getOutputClass())
                .suspendedAt(System.currentTimeMillis())
                .build();
    }

    /**
     * 构建 HITL 挂起状态快照（恢复场景，基于已有 AgentState 继承配置）。
     */
    private AgentState buildSuspendedState(AgentState prev, int step, int maxSteps,
                                           List<Message> messages, List<ToolCall> toolCalls,
                                           int totalPromptTokens, int totalCompletionTokens,
                                           int totalTokens, List<AgentStepResult> stepResults,
                                           ToolRegistry registry) {
        return AgentState.builder()
                .suspendId("suspend-" + suspendIdSeq.incrementAndGet())
                .step(step)
                .maxSteps(maxSteps)
                .messages(new ArrayList<>(messages))
                .pendingToolCalls(new ArrayList<>(toolCalls))
                .totalPromptTokens(totalPromptTokens)
                .totalCompletionTokens(totalCompletionTokens)
                .totalTokens(totalTokens)
                .stepResults(new ArrayList<>(stepResults))
                .tools(prev.getTools())
                .listener(prev.getListener())
                .streamListener(prev.getStreamListener())
                .model(prev.getModel())
                .temperature(prev.getTemperature())
                .stream(prev.getStream())
                .outputClass(prev.getOutputClass())
                .suspendedAt(System.currentTimeMillis())
                .build();
    }

    /**
     * 将 assistant(tool_calls) + tool_result 消息追加到历史。
     */
    private void appendToolMessages(List<Message> messages, List<ToolCall> toolCalls,
                                    Map<String, Object> toolResults) {
        Message assistantMsg = Message.assistantWithToolCalls(toolCalls);
        messages.add(assistantMsg);

        for (ToolCall tc : toolCalls) {
            Object result = toolResults.get(tc.getId());
            String resultStr = result != null ? result.toString() : "[工具执行返回 null]";
            messages.add(Message.toolResult(tc.getId(), tc.getName(), resultStr));
        }
    }

    /**
     * 结构化输出提取。
     */
    @SuppressWarnings("unchecked")
    private <T> Object extractStructuredOutput(String finalText, Class<T> outputClass,
                                               AgentRequest request) {
        try {
            AiRequest extractionReq = AiRequest.builder()
                    .messages(List.of(
                            Message.system("从以下文本中提取结构化信息，严格按 JSON Schema 输出。"),
                            Message.user(finalText)))
                    .responseFormat(AiRequest.ResponseFormat.jsonObject())
                    .build();
            if (request.getModel() != null && !request.getModel().isBlank()) {
                extractionReq.setModel(request.getModel());
            }
            Object output = aiClient.generateObject(extractionReq, outputClass);
            log.debug("结构化输出完成: {}", outputClass.getSimpleName());
            return output;
        } catch (Exception e) {
            log.warn("结构化输出失败，返回原始文本: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 结构化输出提取（恢复场景）。
     */
    @SuppressWarnings("unchecked")
    private <T> Object extractStructuredOutput(String finalText, Class<T> outputClass,
                                               AgentState state) {
        try {
            AiRequest extractionReq = AiRequest.builder()
                    .messages(List.of(
                            Message.system("从以下文本中提取结构化信息，严格按 JSON Schema 输出。"),
                            Message.user(finalText)))
                    .responseFormat(AiRequest.ResponseFormat.jsonObject())
                    .build();
            if (state.getModel() != null && !state.getModel().isBlank()) {
                extractionReq.setModel(state.getModel());
            }
            Object output = aiClient.generateObject(extractionReq, outputClass);
            log.debug("结构化输出完成: {}", outputClass.getSimpleName());
            return output;
        } catch (Exception e) {
            log.warn("结构化输出失败，返回原始文本: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 使用虚拟线程并行执行工具调用。
     * <p>当 LLM 单次返回多个 ToolCall 时，所有工具并发执行，
     * 显著降低总体等待延迟。</p>
     *
     * @param toolCalls      LLM 发起的所有工具调用
     * @param registry       工具注册表
     * @param listener       生命周期事件监听器
     * @param streamListener 流式事件监听器
     * @return callId → 执行结果 的映射
     */
    private Map<String, Object> executeToolsInParallel(
            List<ToolCall> toolCalls,
            ToolRegistry registry,
            AgentEventListener listener,
            AgentStreamListener streamListener) {

        if (toolCalls.size() == 1) {
            ToolCall tc = toolCalls.get(0);
            Object result = executeSingleTool(tc, registry, listener, streamListener);
            Map<String, Object> results = new LinkedHashMap<>();
            results.put(tc.getId(), result);
            return results;
        }

        List<CompletableFuture<Map.Entry<String, Object>>> futures = toolCalls.stream()
                .map(tc -> CompletableFuture.supplyAsync(() -> {
                    Object result = executeSingleTool(tc, registry, listener, streamListener);
                    return Map.entry(tc.getId(), result);
                }, toolExecutor))
                .collect(Collectors.toList());

        Map<String, Object> results = new LinkedHashMap<>();
        for (CompletableFuture<Map.Entry<String, Object>> future : futures) {
            try {
                Map.Entry<String, Object> entry = future.get(60, TimeUnit.SECONDS);
                results.put(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                log.error("工具并行执行超时或异常", e);
                results.put("unknown-" + System.nanoTime(),
                        "工具执行失败: " + e.getMessage());
            }
        }
        return results;
    }

    /**
     * 执行单个工具。
     * <p><b>错误自愈机制</b>：当工具抛出异常时，SDK 捕获异常并将堆栈信息
     * 包装为 toolResult 内容回传给 LLM，驱动 LLM 自行修正参数重试。</p>
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Object executeSingleTool(ToolCall toolCall, ToolRegistry registry,
                                     AgentEventListener listener,
                                     AgentStreamListener streamListener) {
        String toolName = toolCall.getName();
        String argsJson = toolCall.getArguments();

        if (listener != null) {
            listener.onToolStart(toolName, argsJson);
        }
        if (streamListener != null) {
            streamListener.onToolCallStart(toolCall.getId(), toolName, argsJson);
        }

        long toolStart = System.currentTimeMillis();
        AgentTool tool = registry.get(toolName);

        if (tool == null) {
            String error = "工具 [" + toolName + "] 未在注册表中找到，可用工具: "
                    + registry.getAll().stream().map(AgentTool::name).collect(Collectors.joining(", "));
            log.warn(error);
            if (listener != null) {
                listener.onToolEnd(toolName, error);
            }
            if (streamListener != null) {
                streamListener.onToolCallResult(toolCall.getId(), toolName, error);
            }
            return error;
        }

        try {
            Object requestObj;
            Class<?> requestClass = tool.requestClass();
            if (requestClass == Void.class || requestClass == void.class) {
                requestObj = null;
            } else if (argsJson == null || argsJson.isBlank() || "{}".equals(argsJson.trim())) {
                try {
                    requestObj = requestClass.getDeclaredConstructor().newInstance();
                } catch (NoSuchMethodException e) {
                    Map<String, Object> schema = schemaGenerator.generate(requestClass);
                    String schemaJson;
                    try {
                        schemaJson = objectMapper.writeValueAsString(schema);
                    } catch (Exception ex) {
                        schemaJson = schema.toString();
                    }
                    log.warn("工具 {} 需要参数但 LLM 未提供有效 arguments, argsJson=[{}], schema={}",
                            toolName, argsJson, schemaJson);
                    return "错误：工具 [" + toolName + "] 需要参数但未提供有效参数 JSON。"
                            + "请严格按以下 JSON Schema 格式提供参数：\n"
                            + schemaJson;
                }
            } else {
                log.debug("工具 {} 收到 arguments: {}", toolName, argsJson);
                requestObj = objectMapper.readValue(argsJson, requestClass);
            }

            Object result = tool.execute(requestObj);
            long duration = System.currentTimeMillis() - toolStart;
            log.debug("工具 {} 执行成功 ({}ms): {}", toolName, duration,
                    result != null ? result.toString().substring(0, Math.min(100, result.toString().length())) : "null");

            if (listener != null) {
                listener.onToolEnd(toolName, result);
            }
            if (streamListener != null) {
                streamListener.onToolCallResult(toolCall.getId(), toolName, result);
            }

            return result != null ? result : "[工具执行完成，无返回值]";
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - toolStart;
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String errorMsg = String.format(
                    "工具 [%s] 执行失败 (耗时 %dms)。\n错误类型: %s\n错误信息: %s\n堆栈跟踪:\n%s\n"
                            + "请根据以上错误信息修正参数后重试。",
                    toolName, duration, e.getClass().getName(), e.getMessage(), sw.toString());
            log.warn("工具 {} 执行失败 ({}ms): {}", toolName, duration, e.getMessage());

            if (listener != null) {
                listener.onToolEnd(toolName, errorMsg);
            }
            if (streamListener != null) {
                streamListener.onToolCallResult(toolCall.getId(), toolName, errorMsg);
            }

            return errorMsg;
        }
    }
}
