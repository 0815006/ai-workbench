# Apex 项目大模型部分技术选型

> 文档版本：1.0 | 更新日期：2026-08-06 | 基于当前 `main` 分支代码分析

---

## 1. 概述

Apex 项目的大模型（LLM）集成部分采用 **零 AI-SDK 依赖、纯 HTTP 协议直连** 的轻量架构，通过实现 **OpenAI 兼容的 Chat Completions API** 协议，支持接入任意兼容该协议的第三方大模型服务（如 OpenAI、DeepSeek、通义千问等）。整体分为两大模块：

- **经典对话模式（CHAT）**：多轮对话，支持 Skill（自定义 System Prompt）、思考链（reasoning_content）展示
- **Agent 智能体模式（AGENT）**：支持 Function Calling / Tool Use，带工作空间沙箱，AI 可自主执行文件读写、命令执行等操作

---

## 2. 整体架构

```
┌──────────────────────────────────────────────────────────────────┐
│                        前端 (Vue 3)                               │
│                                                                  │
│  ChatView.vue          AgentView.vue                             │
│  ┌─────────────┐       ┌──────────────────┐                      │
│  │ sendChatMessage()   │ sendAgentMessage()│                      │
│  │ (fetch + ReadableStream 手动解析 SSE)    │                      │
│  └──────┬──────┘       └────────┬─────────┘                      │
│         │                       │                                 │
│         └────── POST /api/chat/send ──────┘                      │
│                 (SSE, Accept: text/event-stream)                  │
└───────────────────────┬──────────────────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────────────────┐
│                    后端 (Spring Boot 3.4.4 + JDK 21)               │
│                                                                  │
│  ChatController                                                  │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  @PostMapping("/api/chat/send", produces=SSE)             │    │
│  │  返回 SseEmitter，inside Thread.ofVirtual()               │    │
│  └──────────────────────┬───────────────────────────────────┘    │
│                         │                                        │
│  ChatService                                                    │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  sendMessage() → 分流                                     │    │
│  │    ├─ executeClassicChat()  经典对话                      │    │
│  │    └─ executeAgentLoop()   Agent 循环 (while + tools)     │    │
│  │                                                           │    │
│  │  java.net.http.HttpClient → POST {apiUrl}/chat/completions │    │
│  │  HttpResponse.BodyHandlers.ofLines() → SSE 逐行消费       │    │
│  └──────────────────────┬───────────────────────────────────┘    │
│                         │                                        │
│  LlmService (CRUD)  │  BuiltInToolRegistry (6 大内置工具)        │
│  AiSkillService     │  AgentWorkspaceService                     │
└───────────────────────┬──────────────────────────────────────────┘
                        │
┌───────────────────────▼──────────────────────────────────────────┐
│                    第三方大模型 API                                │
│  OpenAI / DeepSeek / 通义千问 / ...                               │
│  POST /v1/chat/completions                                       │
│  Response: SSE (data: {...}\n\n)                                 │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. 后端技术选型

### 3.1 基础框架

| 技术 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.4.4 | 应用框架 |
| JDK | 21 | 运行环境（启用 Virtual Threads） |
| MyBatis-Plus | 3.5.9 | ORM / 数据库访问 |
| MySQL | - | 关系型数据库 |
| Flyway | - | 数据库迁移管理 |
| Lombok | - | 代码简化 |

### 3.2 核心设计决策：零 AI SDK 依赖

**项目未引入任何 AI 专用 SDK**（如 Spring AI、LangChain4j、OpenAI Java SDK 等），而是直接使用 JDK 内置的 `java.net.http.HttpClient` 发送 HTTP 请求到第三方大模型 API。这个决策的理由和影响：

**优势**：
- **无额外依赖**：不引入 Spring AI（约 30+ 传递依赖），保持 `pom.xml` 极简
- **协议通用**：OpenAI Chat Completions API 已成为事实标准，DeepSeek/通义千问/月之暗面等均兼容此格式，一套代码通吃
- **完全可控**：请求体构造、流式解析、错误处理全由项目自主掌控，无黑盒行为
- **升级灵活**：不受 AI SDK 版本升级的 Breaking Changes 影响

**代价**：
- 需自行实现请求体序列化、SSE 流解析、Tool Calls 的增量拼接等
- 不享受 SDK 提供的自动重试、Token 计数、速率限制等高级功能

### 3.3 HTTP 客户端选型

使用 **`java.net.http.HttpClient`**（JDK 11+ 内置）：

```java
// ChatService.java — 构造 HTTP 请求
HttpRequest httpRequest = HttpRequest.newBuilder()
    .uri(URI.create(chatUrl))                // {apiUrl}/chat/completions
    .header("Authorization", "Bearer " + config.getApiKey())
    .header("Content-Type", "application/json")
    .header("Accept", "text/event-stream")
    .timeout(Duration.ofMinutes(5))
    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
    .build();

HttpClient httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build();

// 使用 ofLines() 将响应体按行消费，天然适配 SSE
HttpResponse<Stream<String>> httpResponse =
    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
```

**选型理由**：
- JDK 内置，零额外依赖
- `BodyHandlers.ofLines()` 返回 `Stream<String>`，天然适配 SSE 的按行读取范式
- 支持 HTTP/2，对长连接流式传输友好
- 配合 JDK 21 虚拟线程，实现高并发低开销的阻塞式编码

**对比**：
| 方案 | 依赖 | 流式支持 | 复杂度 |
|------|------|---------|--------|
| `java.net.http.HttpClient` ✅ | 0（JDK 内置） | `ofLines()` | 低 |
| OkHttp | 2 个 jar | `ResponseBody.source()` | 中 |
| Spring WebClient | 已含（WebFlux） | `Flux<DataBuffer>` | 中高 |
| Spring AI | 30+ 依赖 | 内置封装 | 低（但黑盒） |

### 3.4 流式输出实现（SSE）

#### 3.4.1 整体流程

```
用户发送消息
  → ChatController.sendMessage() 返回 SseEmitter
  → ChatService 在虚拟线程中执行：
      1. 构造 OpenAI 兼容请求体 { model, messages, stream: true }
      2. POST 到第三方 LLM API
      3. 逐行消费 SSE 响应：
         data: {"choices":[{"delta":{"content":"你"}}]}\n\n
         data: {"choices":[{"delta":{"content":"好"}}]}\n\n
         data: [DONE]\n\n
      4. 解析 content/reasoning_content，通过 SseEmitter 逐块推送给前端
      5. 保存完整 AI 回复到数据库
      6. emitter.complete()
```

#### 3.4.2 后端 SSE 发送（Spring MVC SseEmitter）

```java
// ChatService.java
SseEmitter emitter = new SseEmitter(300_000L); // 5 分钟超时

Thread.ofVirtual().start(() -> {
    // ... 调用 LLM API，逐行消费 ...
    while (iterator.hasNext()) {
        String line = iterator.next();
        if (line.startsWith("data: ")) {
            String data = line.substring(6);
            if ("[DONE]".equals(data)) break;

            Map<String, Object> jsonMap = objectMapper.readValue(data, Map.class);
            // 提取 delta.content
            String chunk = (String) delta.get("content");
            fullResponse.append(chunk);

            // 逐块推送给前端
            emitter.send(SseEmitter.event()
                .name("message")
                .data(chunk));
        }
    }
    // 发送完成事件
    emitter.send(SseEmitter.event().name("done")
        .data(Map.of("sessionId", sessionId, "messageId", aiMsg.getId())));
    emitter.complete();
});
```

**SSE 事件类型**：

| 事件名 | 含义 | 数据格式 |
|--------|------|---------|
| `message` | 文本内容增量 | 纯文本字符串 |
| `reasoning` | 思考链增量（DeepSeek-R1 等模型） | 纯文本字符串 |
| `done` | 对话完成 | JSON `{sessionId, messageId}` |
| `error` | 发生错误 | 错误描述字符串 |
| `agent` | Agent 模式统一事件 | JSON，内含 `type` 字段分发 |

#### 3.4.3 非流式降级（防御性设计）

如果不支持流式（或 API 返回非 SSE 格式），代码有自动降级逻辑：

```java
// ChatService.java — 非流式回退
if (!sawSseData && !plainJsonBuffer.isEmpty()) {
    // 尝试作为完整 JSON 解析
    Map<String, Object> jsonMap = objectMapper.readValue(plainJsonBuffer.toString(), Map.class);
    String assistantContent = (String) message.get("content");
    emitter.send(SseEmitter.event().name("message").data(assistantContent));
}
```

#### 3.4.4 并发模型：Virtual Threads

```java
// 使用 JDK 21 虚拟线程，避免阻塞平台线程
Thread.ofVirtual().start(() -> { ... });
```

同时在 `application.yml` 中开启：
```yaml
spring:
  threads:
    virtual:
      enabled: true
```

**选型理由**：SSE 是长连接，传统线程池（如 200 线程）在大量并发 SSE 连接下会耗尽。虚拟线程允许低成本阻塞，一个 SSE 连接占一个虚拟线程但几乎不消耗 OS 线程资源。

### 3.5 Agent 智能体实现

#### 3.5.1 核心机制：While 循环 + Tool Calling

```java
// ChatService.java — Agent 主循环
int loopCount = 0;
while (loopCount < MAX_AGENT_LOOPS && !cancelled.get()) {  // 最多 5 轮
    loopCount++;
    // 1. 构造请求（含 tools 定义 + tool_choice: "auto"）
    LlmRequest llmReq = LlmRequest.fromMessages(
        model, messages, llmStreamEnabled, allTools, "auto");

    // 2. 调用 LLM，流式消费
    // 3. 解析本轮响应：文本内容 + tool_calls（含增量拼接）
    // 4. 如果没有 tool_calls → 纯文本回复，结束循环
    // 5. 如果有 tool_calls → 执行工具，将结果追加到 messages，继续循环
}
```

#### 3.5.2 Tool Calls 增量拼接

OpenAI 流式 Tool Calling 的 `function.arguments` 是分片到达的 JSON 字符串，需要手动拼接：

```java
// 处理 tool_calls delta 增量
if (func.get("arguments") != null) {
    String existingArgs = (String) existingFunc.getOrDefault("arguments", "");
    existing.put("function", Map.of(
        "name", existingFunc.get("name"),
        "arguments", existingArgs + func.get("arguments"))); // 拼接
}
```

#### 3.5.3 内置工具注册表（BuiltInToolRegistry）

硬编码 6 大内置工具，采用 OpenAI Function Calling 标准 JSON Schema：

| 工具名 | 功能 | 分类 |
|--------|------|------|
| `list_dir` | 递归列出工作空间目录树 | 空间感知 |
| `locate_files` | 全局搜索文件/文本（支持正则） | 空间感知 |
| `read_file` | 读取指定文件内容（上限 2MB） | 文件读写 |
| `write_file` | 新建或覆盖文件 | 文件读写 |
| `apply_diff` | 精准替换代码片段 | 精准修改 |
| `execute_command` | 执行 Shell 命令（沙箱限制） | 命令执行 |

**安全沙箱**：
- 路径越界检查：所有文件操作限制在工作空间根目录内
- 命令黑名单：拦截 `rm -rf /`、`wget | sh`、`shutdown`、`reboot` 等
- 命令执行超时：30 秒强制终止 + 销毁子进程树
- Windows/macOS 自适应：`cmd /c` vs `sh -c`

#### 3.5.4 工具执行事件流

Agent 模式下通过统一的 `agent` SSE 事件推送，内含 `type` 字段区分事件类型：

| type | 含义 | 携带字段 |
|------|------|----------|
| `text` | 文本增量 | `content` |
| `reasoning` | 思考链增量 | `reasoning` |
| `tool_start` | 工具开始执行 | `toolName`, `toolCallId` |
| `tool_end` | 工具执行完毕 | `toolName`, `toolCallId`, `status`, `result` |
| `file_changed` | 文件被修改 | `path` |
| `done` | 循环结束 | `sessionId`, `messageId` |
| `error` | 发生错误 | `error` |

### 3.6 LLM 请求体设计（OpenAI 兼容）

```java
// LlmRequest.java — 核心请求体 Record
public record LlmRequest(
    String model,           // 模型名，如 gpt-4o, deepseek-chat
    List<Message> messages, // 对话历史
    boolean stream,         // 是否流式（默认 true）
    List<Map<String, Object>> tools,   // 工具定义（Agent 模式）
    String toolChoice       // "auto" / "none"
) {
    public record Message(
        String role,
        String content,
        List<Map<String, Object>> toolCalls,  // assistant 的工具调用
        String toolCallId,                    // tool 角色的调用 ID
        String name                           // tool 角色的工具名
    ) { }
}
```

**设计要点**：
- `@JsonInclude(NON_NULL)`：`tools`/`tool_choice` 为 null 时不序列化，经典对话模式下请求体不含工具字段
- `@JsonProperty("tool_choice")`：自动映射 Java camelCase 到 JSON snake_case
- 工厂方法重载：`of()` 用于经典模式，`fromMessages()` 用于 Agent 模式

---

## 4. 前端技术选型

### 4.1 基础框架

| 技术 | 版本 | 用途 |
|------|------|------|
| Vue | 3.x | 前端框架 |
| TypeScript | - | 类型安全 |
| Vite | - | 构建工具 |
| Element Plus | - | UI 组件库 |
| Tailwind CSS | - | 原子化样式 |
| marked | - | Markdown → HTML 渲染 |

### 4.2 流式输出消费实现

前端采用 **Fetch API + ReadableStream + 手动 SSE 协议解析**，而非浏览器原生的 `EventSource`：

```typescript
// api/chat.ts — sendChatMessage()
fetch(url, {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-Emp-No': localStorage.getItem('apex_current_emp_no') || '0000000',
  },
  body: JSON.stringify({ sessionId, configId, content, ... }),
  signal: controller.signal,
}).then(async (response) => {
  const reader = response.body?.getReader()  // 获取 ReadableStream reader
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()  // 逐块读取
    if (done) break
    buffer += decoder.decode(value, { stream: true })

    // 手动按行切分，解析 SSE 协议
    const parts = buffer.split('\n')
    buffer = parts.pop() || ''
    for (const line of parts) {
      if (line.startsWith('data: ')) {
        // 派发事件
      }
    }
  }
})
```

#### 4.2.1 为什么不用 EventSource？

| 特性 | EventSource（原生） | Fetch + ReadableStream ✅ |
|------|---------------------|--------------------------|
| HTTP 方法 | 仅 GET | 支持 POST |
| 自定义请求头 | 不支持 | 支持（如 X-Emp-No 身份头） |
| 请求体 | 不支持 | 支持 JSON body |
| 断线重连 | 内置自动重连 | 需自行实现 |
| AbortController | 支持 | 支持 |
| 浏览器兼容性 | 除 IE 外全支持 | 全现代浏览器支持 |

由于本项目的 SSE 接口需要 POST 方法 + JSON 请求体 + 自定义身份头，`EventSource` 无法满足这些需求。

#### 4.2.2 手动 SSE 协议解析逻辑

前端实现了完整的 SSE 协议解析器（内嵌在 `sendChatMessage` / `sendAgentMessage` 中）：

```typescript
// SSE 协议：event: xxx\ndata: yyy\n\n
function parseField(line: string): { field: string; value: string } | null {
  const colonIdx = line.indexOf(':')
  if (colonIdx === -1) return null
  const field = line.substring(0, colonIdx)
  let value = line.substring(colonIdx + 1)
  if (value.startsWith(' ')) value = value.substring(1)  // 跳过冒号后的空格
  return { field, value }
}

// 空行触发事件派发
if (line === '') {
  if (currentEvent && hasData) {
    dispatchEvent(currentEvent, currentData)
  }
  // 重置状态
  currentEvent = ''; currentData = ''; hasData = false
}
```

#### 4.2.3 流式超时与中断

```typescript
// 读超时保护（经典对话 2 分钟，Agent 5 分钟）
const READ_TIMEOUT_MS = 120_000  // 经典对话
const READ_TIMEOUT_MS = 300_000  // Agent 模式

// 每次收到数据重置计时器
function resetTimeout() {
  if (streamTimeout) clearTimeout(streamTimeout)
  streamTimeout = setTimeout(() => {
    controller.abort()
    onError('流式响应超时，长时间未收到数据')
  }, READ_TIMEOUT_MS)
}

// 用户可通过「停止生成」按钮主动中断
function handleStop() { abortStream() }
```

#### 4.2.4 经典对话 vs Agent 模式的 SSE 消费

**经典对话**（`sendChatMessage`）处理 4 种事件：
- `event: message` → `onMessage(chunk)` 逐字追加
- `event: reasoning` → `onReasoning(chunk)` 思考链展示
- `event: done` → `onDone(data)` 保存 AI 回复到消息列表
- `event: error` → `onError(msg)` 错误提示

**Agent 模式**（`sendAgentMessage`）处理：
- `event: agent` → 解析 JSON，按 `type` 分派到不同回调
- 兼容 `event: message` / `event: reasoning` / `event: error`（降级场景）

### 4.3 UI 状态管理

ChatView.vue 的流式输出 UI 状态通过 Vue 3 的 `ref` 管理：

```typescript
const streaming = ref(false)         // 是否正在流式输出
const streamingContent = ref('')     // 当前流式输出的文本（逐字追加）
const streamingReasoning = ref('')   // 当前思考链内容
const reasoningExpanded = ref(true)  // 思考链是否展开
const sending = ref(false)           // 是否正在发送
let currentAbortController: AbortController | null = null  // 中断控制器
```

**流式输出动态渲染**：
```html
<!-- 流式输出中，显示实时内容 + 光标闪烁 -->
<div class="message-bubble streaming" v-html="renderMarkdown(streamingContent || '▊')" />
```

### 4.4 Markdown 渲染

使用 `marked` 库将 LLM 返回的 Markdown 内容渲染为 HTML，配置了 GFM（GitHub Flavored Markdown）支持折行：

```typescript
marked.setOptions({ breaks: true, gfm: true })

function renderMarkdown(text: string): string {
  if (!text) return ''
  try {
    return marked.parse(text) as string
  } catch {
    return text.replace(/</g, '<').replace(/>/g, '>')  // 降级：HTML 转义
  }
}
```

---

## 5. 通信协议设计

### 5.1 请求协议

```
POST /api/chat/send
Content-Type: application/json
Accept: text/event-stream
X-Emp-No: {员工工号}

{
  "sessionId": "xxx" | null,   // null 时自动创建新会话
  "configId": "xxx",           // LLM 配置 ID
  "content": "用户输入内容",
  "skillId": "xxx" | null,     // 可选：绑定的 Skill
  "workspaceId": "xxx" | null  // Agent 模式：工作空间 ID
}
```

后端通过 `workspaceId` 是否为空自动判断进入 CHAT 还是 AGENT 模式。

### 5.2 响应协议（SSE）

```
Content-Type: text/event-stream
Cache-Control: no-cache, no-transform
X-Accel-Buffering: no       // 禁止 Nginx 反向代理缓冲 SSE

event: message
data: 你

event: message
data: 好，我是

event: message
data: AI 助手

event: done
data: {"sessionId":"xxx","messageId":"yyy"}
```

### 5.3 后端到 LLM API 的转发协议

```
POST {apiUrl}/chat/completions
Authorization: Bearer {apiKey}
Content-Type: application/json
Accept: text/event-stream

{
  "model": "gpt-4o",
  "messages": [
    {"role": "system", "content": "你是一个..."},
    {"role": "user", "content": "..."}
  ],
  "stream": true,
  "tools": [...],           // 仅 Agent 模式
  "tool_choice": "auto"     // 仅 Agent 模式
}
```

---

## 6. 数据存储设计

### 6.1 核心表结构

#### llm_config（大模型配置表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(32) | 雪花主键 |
| user_id | VARCHAR(7) | 用户工号（多租户） |
| config_name | VARCHAR(100) | 配置别名（如"个人DeepSeek"） |
| api_url | VARCHAR(500) | Base URL（如 `https://api.openai.com/v1`） |
| api_key | VARCHAR(500) | API 密钥 |
| model_name | VARCHAR(100) | 模型名（如 `gpt-4o`） |
| is_agent_supported | INT | Agent 模式支持标记 |

#### chat_session（会话表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(32) | 雪花主键 |
| user_id | VARCHAR(7) | 用户工号 |
| title | VARCHAR(200) | 会话标题 |
| config_id | VARCHAR(32) | 绑定的 LLM 配置 |
| session_mode | VARCHAR(20) | CHAT / AGENT |
| workspace_id | VARCHAR(32) | Agent 模式的工作空间 |

#### chat_message（消息表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR(32) | 雪花主键 |
| session_id | VARCHAR(32) | 所属会话 |
| role | VARCHAR(20) | user / assistant / tool |
| content | LONGTEXT | 消息内容 |
| tool_name | VARCHAR(100) | 工具名（role=tool 时） |
| tool_call_id | VARCHAR(100) | 工具调用 ID |
| tool_status | VARCHAR(20) | success / failed |
| tool_calls_json | TEXT | assistant 的工具调用 JSON |

### 6.2 Skill 体系

| 表名 | 说明 |
|------|------|
| ai_skill | AI 技能主表（prompt/agent/workflow 三种类型） |
| ai_tool | 原子工具定义表（OpenAI Function Calling 格式） |
| ai_skill_tool_relation | 技能-工具多对多绑定（后续 Agent 阶段使用） |

---

## 7. 安全设计

| 维度 | 措施 |
|------|------|
| 多租户隔离 | 所有数据查询均带 `user_id` = `EmpContext.getEmpNo()` 条件 |
| API Key 保护 | 前端不传输 Key，通过后端代理转发；列表接口不暴露 `api_key` |
| Agent 路径安全 | 所有文件操作强制 `targetPath.startsWith(wsRoot)` 越界检查 |
| 命令执行沙箱 | 正则黑名单拦截 `rm -rf /`、`wget \| sh`、`shutdown` 等高危命令 |
| 隐藏文件保护 | 禁止操作以 `.` 开头的文件，防止篡改 `.gitignore`、`.env` 等 |
| 文件大小限制 | `read_file` 上限 2MB，上传文件 `${FILE_MAX_SIZE:200MB}` |

---

## 8. 总结

Apex 项目的大模型集成采用 **极简主义** 的技术路线：

### 核心选型总结

| 层次 | 选型 | 核心理由 |
|------|------|---------|
| AI 协议 | OpenAI Chat Completions 兼容 | 行业事实标准，跨厂商通用 |
| 后端 HTTP | `java.net.http.HttpClient` | JDK 内置，零依赖，`ofLines()` 天然适配 SSE |
| 后端流式 | Spring MVC `SseEmitter` | Servlet 生态原生支持，无需引入 WebFlux |
| 并发模型 | JDK 21 Virtual Threads | 低成本阻塞，大幅简化异步编码 |
| 前端流式消费 | Fetch + ReadableStream + 手动 SSE 解析 | 支持 POST + 自定义头，EventSource 无法满足 |
| Markdown 渲染 | `marked`（GFM） | 轻量、零配置 |
| AI 框架 | **无**（零 AI SDK） | 完全自主可控，无黑盒依赖 |
| Agent 工具 | 硬编码 6 大内置工具（OpenAI Function Schema） | 零配置、随源码打包、明确的安全边界 |
| 数据库 | MySQL + MyBatis-Plus + Flyway | 成熟可靠，迁移可追溯 |
| 工作空间隔离 | 文件系统路径 + 越界检查 | 简单直接，不引入容器/Docker 复杂度 |

### 架构优势

1. **依赖极简**：后端 `pom.xml` 仅 7 个核心依赖（Web、MyBatis-Plus、MySQL、Flyway、Lombok、Actuator、Validation），无 AI SDK 的依赖爆炸问题
2. **厂商无关**：通过 OpenAI 兼容协议，可接入 DeepSeek、通义千问、月之暗面等任意兼容厂商，切换模型只需修改配置表中的 `api_url` 和 `api_key`
3. **自定义 Skill**：用户可为对话绑定预置 System Prompt（Skill），实现角色扮演、代码审查等场景
4. **Agent 沙箱**：自主实现的工具注册 + 路径沙箱 + 命令黑名单，在灵活性和安全性之间取得平衡
5. **双模分流**：同一接口根据 `workspaceId` 自动切换经典对话 / Agent 智能体模式，前端统一消费

### 待完善方向（当前代码中的 TODO/预留）

- Agent 模式的外部插件注册机制（`ai_tool` 表已建，`BuiltInToolRegistry` 已预留分支但标记 "MVP 阶段暂不实现"）
- 流式输出的断线重连（当前发生异常直接终止 SSE）
- Token 用量统计与计费
- 请求级速率限制（Rate Limiting）
