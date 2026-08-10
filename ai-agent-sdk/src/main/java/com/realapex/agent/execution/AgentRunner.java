package com.realapex.agent.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realapex.agent.context.ContextTrimmer;
import com.realapex.agent.event.AgentEventListener;
import com.realapex.agent.exception.AgentMaxStepsExceededException;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.schema.SchemaGenerator;
import com.realapex.agent.tool.ToolRegistry;
import com.realapex.client.client.AiClient;
import com.realapex.client.client.StreamListener;
import com.realapex.client.model.AiRequest;
import com.realapex.client.model.AiResponse;
import com.realapex.client.model.Message;
import com.realapex.client.model.ToolCall;
import com.realapex.client.model.ToolDefinition;
import com.realapex.client.model.Usage;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * ReAct (Thought-Action-Observation) 循环驱动器。
 * <p>这是 ai-agent-sdk 的核心引擎，负责驱动大模型在 while 循环中
 * 自主决策、调用工具、观察结果，直到任务完成或达到步数上限。</p>
 *
 * <h3>线程安全</h3>
 * <p>本实例是线程安全的单例。所有多轮对话状态限定在单次 {@link #run(AgentRequest)}
 * 调用作用域（局部变量），不共享任何跨请求的可变状态。</p>
 *
 * <h3>ReAct 循环流程</h3>
 * <ol>
 *   <li>构建初始消息列表（system + user + 附加上下文）</li>
 *   <li>调用 LLM → 解析响应</li>
 *   <li>若无 tool_calls → 返回最终文本</li>
 *   <li>若有 tool_calls → 虚拟线程并行执行所有工具</li>
 *   <li>将工具结果拼装为 assistant(tool_calls) + tool_result 消息追加到历史</li>
 *   <li>上下文裁剪检查 → 回到步骤 2</li>
 * </ol>
 *
 * <h3>流式模式（默认启用）</h3>
 * <p>当 {@link AgentRequest#getStream()} 为 true 时，通过
 * {@link AiClient#generate(AiRequest, StreamListener)} 走 SSE 流式路径——
 * 底层逐字推送 {@link AgentEventListener#onChunk(String)}，同时内部累积
 * 文本和工具调用，最终仍返回完整的 {@link AiResponse}（含 tool_calls、usage）。</p>
 */
@Slf4j
public class AgentRunner {

    private final AiClient aiClient;
    private final SchemaGenerator schemaGenerator;
    private final ObjectMapper objectMapper;
    private final ExecutorService toolExecutor;
    private final ContextTrimmer contextTrimmer;

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
     *
     * @param request Agent 请求配置
     * @return Agent 执行结果
     * @throws AgentMaxStepsExceededException 达到 maxSteps 仍未完成时抛出
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
     * @throws AgentMaxStepsExceededException 达到 maxSteps 仍未完成时抛出
     */
    @SuppressWarnings("unchecked")
    public <T> AgentResult run(AgentRequest request, Class<T> outputClass) {
        return doRun(request, outputClass);
    }

    // ==================== 核心 ReAct 循环 ====================

    @SuppressWarnings("unchecked")
    private <T> AgentResult doRun(AgentRequest request, Class<T> outputClass) {
        long startTime = System.currentTimeMillis();
        AgentEventListener listener = request.getListener();
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
            // 流式路径：generate(req, listener) → SSE 实时推送 onChunk + 返回完整 AiResponse
            // 非流式路径：generate(req) → 传统同步 HTTP → 返回完整 AiResponse
            // 两条路径统一返回 AiResponse，后续处理完全一致
            AiResponse aiResponse;
            if (streaming) {
                aiResponse = aiClient.generate(aiReq, new StreamListener() {
                    @Override
                    public void onChunk(String chunk) {
                        if (listener != null) {
                            listener.onChunk(chunk);
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
            } else {
                aiResponse = aiClient.generate(aiReq);
            }

            // === 提取响应信息（两条路径统一从 AiResponse 取值） ===
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

            // === 并行执行工具 ===
            Map<String, Object> toolResults = executeToolsInParallel(toolCalls, registry, listener);

            // === 将 assistant(tool_calls) + tool_results 追加到消息历史 ===
            Message assistantMsg = Message.assistantWithToolCalls(toolCalls);
            messages.add(assistantMsg);

            for (ToolCall tc : toolCalls) {
                Object result = toolResults.get(tc.getId());
                String resultStr = result != null ? result.toString() : "[工具执行返回 null]";
                messages.add(Message.toolResult(tc.getId(), tc.getName(), resultStr));
            }

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
                structuredOutput = aiClient.generateObject(extractionReq, outputClass);
                log.debug("结构化输出完成: {}", outputClass.getSimpleName());
            } catch (Exception e) {
                log.warn("结构化输出失败，返回原始文本: {}", e.getMessage());
                structuredOutput = null;
            }
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

        // === 事件：onComplete ===
        if (listener != null) {
            listener.onComplete(result);
        }

        // === 熔断 ===
        if (maxStepsExceeded) {
            throw new AgentMaxStepsExceededException(maxSteps, result);
        }

        return result;
    }

    // ==================== 请求构建 ====================

    /**
     * 构建 AiRequest，合并 AgentRequest 中的模型/温度配置。
     * <p>注意：不设置 stream 标志——由 {@link AiClient#generate(AiRequest)}
     * 和 {@link AiClient#generate(AiRequest, StreamListener)} 内部根据方法签名
     * 自动确定流式/非流式路径。</p>
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

    // ==================== 内部方法 ====================

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
     * 使用虚拟线程并行执行工具调用。
     * <p>当 LLM 单次返回多个 ToolCall 时，所有工具并发执行，
     * 显著降低总体等待延迟。</p>
     *
     * @param toolCalls LLM 发起的所有工具调用
     * @param registry  工具注册表
     * @param listener  事件监听器
     * @return callId → 执行结果 的映射
     */
    private Map<String, Object> executeToolsInParallel(
            List<ToolCall> toolCalls,
            ToolRegistry registry,
            AgentEventListener listener) {

        if (toolCalls.size() == 1) {
            ToolCall tc = toolCalls.get(0);
            Object result = executeSingleTool(tc, registry, listener);
            Map<String, Object> results = new LinkedHashMap<>();
            results.put(tc.getId(), result);
            return results;
        }

        List<CompletableFuture<Map.Entry<String, Object>>> futures = toolCalls.stream()
                .map(tc -> CompletableFuture.supplyAsync(() -> {
                    Object result = executeSingleTool(tc, registry, listener);
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
                                      AgentEventListener listener) {
        String toolName = toolCall.getName();
        String argsJson = toolCall.getArguments();

        if (listener != null) {
            listener.onToolStart(toolName, argsJson);
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
                    log.warn("工具 {} 需要参数但 LLM 未提供有效 arguments", toolName);
                    return "错误：工具 [" + toolName + "] 需要参数但未提供有效参数 JSON。"
                            + "请提供符合以下 Schema 的 JSON 参数。";
                }
            } else {
                requestObj = objectMapper.readValue(argsJson, requestClass);
            }

            Object result = tool.execute(requestObj);
            long duration = System.currentTimeMillis() - toolStart;
            log.debug("工具 {} 执行成功 ({}ms): {}", toolName, duration,
                    result != null ? result.toString().substring(0, Math.min(100, result.toString().length())) : "null");

            if (listener != null) {
                listener.onToolEnd(toolName, result);
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

            return errorMsg;
        }
    }
}
