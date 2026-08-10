# 当前 SDK 能力清单

> 更新时间：2026-08-10 | 基于"铁三角"架构：`ai-agent-sdk → ai-tool-sdk → ai-client-sdk`

---

## 一、ai-client-sdk — 通信基座

### 1.1 核心接口

| 能力 | API | 说明 |
|---|---|---|
| 同步文本生成 | `AiClient.generateText(AiRequest)` | 返回纯文本字符串 |
| 完整响应生成 | `AiClient.generate(AiRequest)` | 返回 AiResponse（含 tool_calls、usage、finish_reason） |
| SSE 流式输出 | `AiClient.streamText(AiRequest, StreamListener)` | 打字机效果，增量推送 |
| 结构化 JSON 输出 | `AiClient.generateObject(AiRequest, Class\<T\>)` | JSON → 强类型 Java 对象 |

### 1.2 消息模型

| 能力 | 类 | 说明 |
|---|---|---|
| 多角色消息 | `Message` | system / user / assistant / tool 四种角色 |
| 工具调用消息 | `Message.assistantWithToolCalls()` / `Message.toolResult()` | 含 tool_call_id、tool_calls 字段 |
| 请求构建 | `AiRequest` | Builder 模式，支持 tools、tool_choice、response_format、temperature |
| 响应解析 | `AiResponse` | getText() / getToolCalls() / getFinishReason() / getUsage() |
| 工具定义 | `ToolDefinition` | OpenAI Function Calling 格式（name、description、parameters） |
| 工具调用 | `ToolCall` | id、name、arguments，静态工厂 `ToolCall.of()` |
| 工具选择策略 | `ToolChoice` | AUTO / NONE / REQUIRED / function(name)，Jackson 自定义序列化 |
| Token 统计 | `Usage` | promptTokens / completionTokens / totalTokens |

### 1.3 SSE 流式事件

| 能力 | 类/接口 | 说明 |
|---|---|---|
| 流式事件密封体系 | `StreamEvent` (Sealed Interface) | TextChunk / ToolCallChunk / UsageEvent / Complete |
| ToolCall 增量缓冲 | `StreamToolCallBuffer` | 按 callId 拼装增量 delta.tool_calls |
| 流式回调 | `StreamListener` | onChunk / onToolCallChunk / onUsage / onComplete / onError |

### 1.4 容错与高可用

| 能力 | 类 | 说明 |
|---|---|---|
| 指数退避重试 | `RetryHandler` | 429/5xx 自动重试，公式：baseDelay × 2^attempt + random(0,1000ms) |
| Key 轮询与隔离 | `KeySelector` | Round-Robin 轮询，401/402 自动黑名单隔离（默认 10 分钟），自动过期恢复 |
| JSON 容错解析 | `JsonRepairParser` | 自动清洗 Markdown 代码块（```json）、提取裸 JSON、支持对象和数组 |

### 1.5 配置

| 能力 | 类 | 说明 |
|---|---|---|
| 编程式配置 | `AiConfig` | Builder 模式：baseUrl、apiKeys、model、timeout、readTimeout、connectTimeout、maxRetries |
| Spring 自动配置 | `AiSdkAutoConfiguration` | 读取 `ai.sdk.*` 前缀配置，自动创建 AiClient Bean |
| 配置属性绑定 | `AiSdkProperties` | @ConfigurationProperties，支持 IDE 提示 |

### 1.6 异常体系

| 异常 | 触发条件 |
|---|---|
| `AiClientException` | 根异常 |
| `RateLimitException` | 429 限流 |
| `AuthenticationException` | 401/402 认证失败 |
| `ParseException` | JSON 解析/提取失败 |

### 1.7 扩展机制

| 能力 | 类 | 说明 |
|---|---|---|
| 技能抽象 | `BaseSkill<I,O>` | 确定性单步技能基类，继承实现 execute(client, input) |
| 技能上下文 | `SkillContext` | 跨步骤共享状态存储（ConcurrentHashMap） |
| Schema 注解 | `@AiSchema` | 标记类用于结构化输出的 Schema 生成 |

### 1.8 遗留存根（未实现）

| 类 | 说明 |
|---|---|
| `agent/AgentExecutor` | "二期" TODO，已被 ai-agent-sdk 的 AgentRunner 取代 |
| `agent/Tool` | "二期" TODO，已被 ai-tool-sdk 的 AgentTool 取代 |

---

## 二、ai-tool-sdk — 工具基座

### 2.1 工具契约

| 能力 | 类/接口 | 说明 |
|---|---|---|
| 工具统一接口 | `AgentTool<REQ, RESP>` | name() / description() / requestClass() / execute() |
| 安全执行包装 | `AgentTool.executeSafely(REQ)` | 默认方法，异常 → ToolResult.fail() |
| 标准返回包装 | `ToolResult` | success / data / error / durationMs，静态工厂 ok() / fail() |

### 2.2 注解体系

| 能力 | 注解 | 说明 |
|---|---|---|
| 工具标记 | `@Tool` | @Target(METHOD, TYPE)，属性：name、description、readOnly |
| 参数描述 | `@ToolParam` | @Target(FIELD, PARAMETER, RECORD_COMPONENT)，属性：description、required |

### 2.3 Schema 生成

| 能力 | 类 | 说明 |
|---|---|---|
| JSON Schema 生成 | `SchemaGenerator` | Jackson 反射 Record/POJO → JSON Schema，@ToolParam 增强，ConcurrentHashMap 缓存 |
| 类型映射 | — | String→string, int→integer, double→number, boolean→boolean, List→array, Enum→enum, Record→object |

### 2.4 安全沙箱

| 能力 | 类/接口 | 优先级 | 说明 |
|---|---|---|---|
| 拦截器接口 | `ToolSecurityInterceptor` | — | before / after / onError 钩子，priority 排序 |
| 参数校验 | `ParamValidator` | 10 | 校验 @ToolParam(required=true) 字段非空 |
| 危险命令过滤 | `DangerousCommandFilter` | 20 | 11 种正则检测：SQL 注入（DROP TABLE/DELETE FROM/TRUNCATE/ALTER...DROP）、系统命令（rm -rf/shutdown/reboot/chmod 777/mkfs/dd if=）、代码注入（eval/exec/Runtime.getRuntime/ProcessBuilder）、路径遍历（../、..\\） |
| 超时控制 | `TimeoutInterceptor` | 50 | CompletableFuture.orTimeout()，默认 30s |

---

## 三、ai-agent-sdk — 编排引擎

### 3.1 ReAct 循环

| 能力 | 类 | 说明 |
|---|---|---|
| 核心引擎 | `AgentRunner` | 单例线程安全，while(step < maxSteps) 循环，状态限定单次调用作用域 |
| 请求模型 | `AgentRequest` | Builder 模式：systemPrompt / userPrompt / tools / maxSteps / listener / model / temperature / maxContextTokens / outputClass |
| 执行结果 | `AgentResult` | finalText / structuredOutput / totalSteps / totalUsage / totalDurationMs / stepResults |
| 单步详情 | `AgentStepResult` | stepNumber / llmResponse / toolCalls / toolResults / usage / durationMs |
| 结构化输出 | `AgentRunner.run(req, Class\<T\>)` | ReAct 循环结束后调用 generateObject 提取强类型结果 |

### 3.2 工具注册与调度

| 能力 | 类 | 说明 |
|---|---|---|
| 工具注册表 | `ToolRegistry` | 线程安全 ConcurrentHashMap，register / registerMethodTool / unregister / get / getAll / getToolDefinitions |
| 并行工具执行 | `AgentRunner.executeToolsInParallel()` | 单工具直接执行，多工具虚拟线程 CompletableFuture.allOf() 并发，60s 超时 |
| 错误自愈 | `AgentRunner.executeSingleTool()` | 工具异常 → 捕获堆栈 → 包装为 toolResult 回传 LLM → 驱动自动修正重试 |
| 参数反序列化 | — | Jackson ObjectMapper 将 LLM arguments JSON 反序列化为工具 requestClass |

### 3.3 上下文裁剪

| 能力 | 类 | 说明 |
|---|---|---|
| Token 裁剪 | `ContextTrimmer` | 滑动窗口 + ~4 chars/token 估算，超出预算自动裁剪 |
| Tool 配对规则 | — | 裁剪 tool 消息 → 同步移除对应的 assistant(tool_calls) 消息 |
| 保护策略 | — | system 消息 + 最新 user 消息永不裁剪 |

### 3.4 生命周期事件

| 能力 | 接口 | 说明 |
|---|---|---|
| 事件监听 | `AgentEventListener` | 全默认方法：onStepStart / onStepFinish / onToolStart / onToolEnd / onChunk / onComplete |
| 步进事件 | onStepStart / onStepFinish | 每轮 ReAct 循环的起止回调 |
| 工具事件 | onToolStart / onToolEnd | 单个工具执行的起止回调 |

### 3.5 异常与熔断

| 能力 | 类 | 说明 |
|---|---|---|
| 超步熔断 | `AgentMaxStepsExceededException` | 达到 maxSteps 仍无最终答案时抛出，携带部分 AgentResult |

### 3.6 Spring 自动配置

| 能力 | 类 | 说明 |
|---|---|---|
| 自动装配 | `AgentAutoConfiguration` | @ConditionalOnBean(AiClient)，自动创建 SchemaGenerator / ToolRegistry / AgentRunner |
| 注解扫描 | `ToolBeanPostProcessor` | BeanPostProcessor，扫描所有 Bean 的 @Tool 方法自动 registerMethodTool |
| 配置属性 | `AgentProperties` | @ConfigurationProperties(prefix="ai.agent")：maxSteps / maxContextTokens / model |

---

## 四、模块依赖关系

```
ai-agent-sdk  ──→  ai-tool-sdk  ──→  ai-client-sdk
(编排引擎)          (工具基座)         (通信基座)
```

- ai-agent-sdk 依赖 ai-tool-sdk + ai-client-sdk
- ai-tool-sdk 仅依赖 ai-client-sdk
- 禁止反向依赖

---

## 五、工程统计

| 指标 | ai-client-sdk | ai-tool-sdk | ai-agent-sdk | 合计 |
|---|---|---|---|---|
| Java 源文件 | 22 | 8 | 9 | **39** |
| 公开接口/类 | ~28 | ~9 | ~11 | **~48** |
| 包数 | 9 | 4 | 5 | **18** |

---

## 六、待建设能力（后续迭代）

| 模块 | 能力 | 优先级 |
|---|---|---|
| ai-client-sdk | 多厂商适配层（DeepSeek / OpenAI / 通义千问） | P1 |
| ai-client-sdk | 本地模型支持（Ollama / vLLM） | P2 |
| ai-tool-sdk | 内置通用工具（CalculatorTool 等） | P2 |
| ai-agent-sdk | Human-in-the-Loop（人工确认节点） | P1 |
| ai-agent-sdk | 流式 Agent（SSE 中间步骤推送） | P1 |
| 新模块 | 领域工具扩展包（ai-tool-db / ai-tool-ops / ai-tool-k8s） | P2 |
