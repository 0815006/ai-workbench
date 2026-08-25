package com.realapex.client.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realapex.client.model.AiRequest;
import com.realapex.client.model.AiResponse;
import com.realapex.client.model.Message;
import com.realapex.client.model.ToolDefinition;

import java.util.ArrayList;
import java.util.List;

/**
 * Ollama / vLLM 本地模型适配器。
 * <p>本地模型（Qwen、Llama、DeepSeek 蒸馏版等）在 Tool Calling 支持上\n * 参差不齐，本适配器提供两层降级策略：</p>
 * <ul>
 *   <li><b>Tool Prompt 注入</b>：将工具定义以自然语言提示词注入 system 消息，\n *       引导模型输出 JSON 格式的工具调用（无需原生 Function Calling）</li>
 *   <li><b>JSON 降级解析</b>：当模型返回非标准 JSON 时，通过容错解析器提取工具调用</li>
 * </ul>
 *
 * <h3>典型模型</h3>
 * <ul>
 *   <li>qwen2.5 / qwen3（阿里通义本地版）</li>
 *   <li>llama3.x（Meta）</li>
 *   <li>deepseek-r1:distill（蒸馏版）</li>
 * </ul>
 */
public class OllamaModelProvider implements ModelProvider {

    private final ObjectMapper objectMapper;

    public OllamaModelProvider() {
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String providerName() {
        return "ollama";
    }

    @Override
    public boolean supportsReasoning() {
        return true;
    }

    /**
     * 请求适配：将工具定义注入为 system 提示词，引导本地模型输出 JSON 工具调用。
     * <p>当请求携带 tools 时，追加一条 system 消息描述可用工具与 JSON 输出格式，\n * 使不支持原生 Function Calling 的本地模型也能完成工具调用。</p>
     *
     * @param request 原始请求
     * @return 注入 Tool Prompt 后的请求
     */
    @Override
    public AiRequest adaptRequest(AiRequest request) {
        if (request.getTools() == null || request.getTools().isEmpty()) {
            return request;
        }

        List<Message> messages = new ArrayList<>(request.getMessages());
        StringBuilder toolPrompt = new StringBuilder();
        toolPrompt.append("你是一个 AI 助手，可以使用以下工具完成任务。\n");
        toolPrompt.append("当需要调用工具时，请严格输出如下 JSON 格式（不要输出其他内容）：\n");
        toolPrompt.append("{\"tool\": \"工具名\", \"arguments\": {参数对象}}\n\n");
        toolPrompt.append("可用工具列表：\n");

        for (ToolDefinition def : request.getTools()) {
            if (def.getFunction() == null) {
                continue;
            }
            toolPrompt.append("- ").append(def.getFunction().getName())
                    .append(": ").append(def.getFunction().getDescription()).append("\n");
        }

        messages.add(0, Message.system(toolPrompt.toString()));
        request.setMessages(messages);
        return request;
    }

    @Override
    public String extractReasoning(AiResponse response) {
        // 本地模型通常无 reasoning_content，返回 null 走标准路径
        return null;
    }
}