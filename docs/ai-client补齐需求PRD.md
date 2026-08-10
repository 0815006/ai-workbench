这份整合并重新梳理后的 **`ai-client-sdk` 升级与能力补齐需求文档（PRD）**，已将前面的讨论内容（包括 Tool Calling 协议、SSE 增量拼装、Usage 统计、ToolChoice 策略、厂商容错解析及分层超时控制）统一收拢为一份规范、完整的工程需求规范。

---

# 产品需求规范：`ai-client-sdk` 升级与能力补齐规范

| 文档版本 | 创建日期 | 状态 | 目标模块 | 技术基线 |
| --- | --- | --- | --- | --- |
| **v1.1.0** | 2026-08-10 | 🚀 评审通过 / 待迭代 | `ai-client-sdk` | Java 21 / 原生 HttpClient |

---

## 1. 升级背景与核心目标

### 1.1 背景说明

当前 `ai-client-sdk` 是一个优秀的 **Chat & 结构化 JSON 基础 SDK**，但在面向 Agent 编排架构（如 `ai-agent-sdk` 的 ReAct 状态机循环）时，缺乏对 **Tool Calling（函数调用）协议、SSE 增量拼装、Token 消耗透出及高可用容错** 的底层原语支持。

### 1.2 升级目标

补齐 OpenAI 兼容规范中的 Tool Calling 全流程通信能力，健全多厂商模型容错机制与分层超时控制，使其升级为**完全体、企业级的 AI 通信基座 SDK**。

---

## 2. 补齐功能与工程细节矩阵

```
┌────────────────────────────────────────────────────────┐
│                   ai-client-sdk (完全体)                │
│ ┌──────────────────────┐   ┌─────────────────────────┐ │
│ │ Model 协议协议支持    │   │ 流式与事件 (SSE Buffer) │ │
│ │ • Tools / ToolChoice │   │ • TextChunk             │ │
│ │ • Message (Role.TOOL)│   │ • ToolCallChunk (拼接)  │ │
│ └──────────────────────┘   └─────────────────────────┘ │
│ ┌──────────────────────┐   ┌─────────────────────────┐ │
│ │ 治理与容错            │   │ 传输与计费              │ │
│ │ • JsonUtils 强容错   │   │ • Usage (Token 计费)    │ │
│ │ • 增量 Buffer 容错   │   │ • Tiered Timeout 分层超时│ │
│ └──────────────────────┘   └─────────────────────────┘ │
└────────────────────────────────────────────────────────┘

```

---

### 2.1 模块 1：Tool/Function Calling 基础协议扩展 (`model` 包)

| 需求编号 | 功能点 | 详细说明 | 优先级 |
| --- | --- | --- | --- |
| **FR-CLIENT-01** | **`AiRequest.tools` 定义** | 增加 `tools(List<ToolDefinition>)` 字段，支持向 LLM 传输标准 JSON Schema 描述的工具列表。 | P0 |
| **FR-CLIENT-02** | **`Message` 多角色与结构扩展** | **对齐 OpenAI 协议规范**：<br>

<br>• 支持 `Message.assistantToolCall(id, name, args)`（记录模型发起的调工请求）；<br>

<br>• 支持 `Message.toolResult(toolCallId, content)`（回传工具执行结果，Role 为 `tool`）。 | P0 |
| **FR-CLIENT-03** | **`AiResponse.toolCalls` 解析** | 确保响应体能够精准提取并结构化解析出 `List<ToolCall>`（包含 `id`, `name`, `arguments`），并提供快捷方法 `hasToolCalls()`。 | P0 |
| **FR-CLIENT-04** | **`ToolChoice` 原生策略配置** | **【补齐】** 支持 `toolChoice` 配置选项：<br>

<br>• `ToolChoice.AUTO`（默认，模型自决）；<br>

<br>• `ToolChoice.NONE`（禁用调工）；<br>

<br>• `ToolChoice.REQUIRED`（强迫触发工具）；<br>

<br>• `ToolChoice.function(name)`（指定调用某工具，常用于结构化提取）。 | P1 |

---

### 2.2 模块 2：SSE 流式传输下的 Tool Call 增量拼装 (`stream` 包)

| 需求编号 | 功能点 | 详细说明 | 优先级 |
| --- | --- | --- | --- |
| **FR-CLIENT-05** | **`StreamEvent` 密封接口抽象** | 基于 Java 21 `sealed interface` 统一抽象 SSE 返回事件：<br>

<br>• `TextChunkEvent`：文本增量；<br>

<br>• `ToolCallChunkEvent`：工具指令增量；<br>

<br>• `UsageEvent`：最后一帧的 Token 统计。 | P0 |
| **FR-CLIENT-06** | **`StreamToolCallBuffer` 增量拼接** | 由于 SSE 返回的 `delta.tool_calls`（如 `arguments`）是分散片段，SDK 内部必须提供 Buffer 收集器，在流传输完成或触发事件时，自动拼接为完整的 `ToolCall` 对象。 | P0 |

---

### 2.3 模块 3：厂商容错解析与大文本防护 (`util` 包)

| 需求编号 | 功能点 | 详细说明 | 优先级 |
| --- | --- | --- | --- |
| **FR-CLIENT-07** | **多厂商 JSON 转义容错 (Vendor Divergence)** | **【补齐】** 部分厂商模型（如 DeepSeek/Qwen 等）在流式输出 `arguments` 时首帧为空或包含未转义换行符。扩展 Jackson 配置（开启 `ALLOW_UNQUOTED_CONTROL_CHARS` 等），确保解析 `arguments` 不引发反序列化崩溃。 | P0 |
| **FR-CLIENT-08** | **Markdown 清理工具 `JsonUtils**` | 提供内置提炼方法，自动剥离 Markdown 标识（如 ````json ... ````）并提取最内层 JSON 结构反序列化。 | P1 |

---

### 2.4 模块 4：Token 计费透出与网络分层超时 (`governance` 包)

| 需求编号 | 功能点 | 详细说明 | 优先级 |
| --- | --- | --- | --- |
| **FR-CLIENT-09** | **全通道 `Usage` Token 统计透出** | 不管是 `generate()` 同步接口，还是 `stream()` SSE 流式接口（最后一帧），必须提取并封装 `Usage` 对象（包含 `promptTokens`, `completionTokens`, `totalTokens`），供应用层或 Agent 计算成本与裁切上下文。 | P0 |
| **FR-CLIENT-10** | **网络层分层超时控制 (Tiered Timeout)** | **【补齐】** 区分文本对话与工具调用的延迟特性，配置项提供细粒度分层：<br>

<br>• `connectTimeout`：握手建立超时（默认 5s）；<br>

<br>• `readTimeout` / `socketTimeout`：SSE 无数据包接收超时（默认 30s），防止长轮询连接无声挂起。 | P1 |
| **FR-CLIENT-11** | **HTTP 429 指数退避重试** | 针对 API 限流（429）和服务器暂态故障（5xx），内置指数退避重试（Jitter Backoff），确保上层 Agent 不因单次网络抖动崩溃。 | P1 |

---

## 3. 补齐后的理想 SDK 接口形态

补充上述能力后，应用层在不引入 Agent 框架时，也可单凭 `ai-client-sdk` 优雅地进行工具调用与 Token 计费：

```java
// 1. 构建完全体请求（支持 Messages 历史、Tools 定义与 ToolChoice 约束）
AiRequest request = AiRequest.builder()
        .messages(List.of(
            Message.system("你是 MySQL DB 优化专家"),
            Message.user("分析慢 SQL: SELECT * FROM orders WHERE user_id = 100"),
            // 历史轮次中的 Tool 交互记录
            Message.assistantToolCall("call_idx001", "explain_sql", "{\"sql\":\"SELECT...\"}"),
            Message.toolResult("call_idx001", "{\"type\":\"ALL\",\"rows\":500000}")
        ))
        .tools(List.of(explainSqlToolSchema))  // 传入工具定义
        .toolChoice(ToolChoice.AUTO)           // 自动决定调工或文本输出
        .temperature(0.1)
        .build();

// 2. 发起请求
AiResponse response = aiClient.generate(request);

// 3. 业务分支逻辑判定
if (response.hasToolCalls()) {
    for (ToolCall call : response.getToolCalls()) {
        System.out.println("大模型发起调工请求 ID: " + call.getId());
        System.out.println("调工目标函数: " + call.getName());
        System.out.println("调工参数 JSON: " + call.getArguments());
    }
} else {
    System.out.println("大模型直接回答文本: " + response.getText());
}

// 4. 统计消费的 Token 数量
Usage usage = response.getUsage();
System.out.println(String.format("Token 消耗 - Prompt: %d, Completion: %d, Total: %d",
        usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens()));

```

---

## 4. 总结与开发落地优先级

本次补齐使 `ai-client-sdk` 的边界从单纯的“聊天与 JSON 转换器”，升级为了**面向生产环境的无状态 AI 协议基座**。

* **第一优先级 (Phase 1)**：完成 `Message`（角色与 `toolCallId`）、`ToolDefinition`、`ToolCall` 的 DTO 改造，以及 `AiResponse.getUsage()` 透出。
* **第二优先级 (Phase 2)**：完成 SSE `StreamToolCallBuffer` 增量拼接与 `ToolChoice` 参数绑定。
* **第三优先级 (Phase 3)**：完成多厂商 JSON 容错与 `Tiered Timeout` 分层超时调整。