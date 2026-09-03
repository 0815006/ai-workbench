# AI Workbench SDK

轻量级、低耦合的 AI 辅助开发 SDK 集合，基于 **"铁三角"架构**：`ai-agent-sdk → ai-tool-sdk → ai-client-sdk`。

## 模块一览

| 模块 | 定位 | 说明 |
|---|---|---|
| `ai-client-sdk` | 通信基座 | HTTP/SSE 传输、Tool Calling 协议、Key 轮询、重试熔断 |
| `ai-tool-sdk` | 工具基座 | AgentTool 契约、@Tool/@ToolParam 注解、Schema 生成、安全沙箱 |
| `ai-agent-sdk` | 编排引擎 | ReAct 循环、虚拟线程并发调度、Token 裁剪、生命周期事件 |
| `ai-tool-doc` | 领域工具包 | 文档转换/模板渲染：Word/Excel/PDF 解析、模板填充 |
| `ai-tool-db` | 领域工具包 | 数据库工具：Schema 探查、只读查询、EXPLAIN 诊断、慢查询抓取、受控写操作（HITL） |

## 技术亮点

- **零第三方 HTTP 依赖** — JDK 21 原生 `HttpClient` + 虚拟线程，Jar 极轻
- **零 AI 框架** — 不引入 Spring AI / LangChain4j，直接基于 OpenAI 兼容协议手写 DTO
- **框架无关** — 纯 Java 可用，Spring Boot 自动装配可选
- **高可用内置** — API Key 轮询、故障隔离、指数退避重试、JSON 容错开箱即用
- **安全沙箱** — 工具执行链式拦截：参数校验 → 危险命令过滤 → 超时控制
- **流式 Agent** — `AgentStreamEvent` 密封事件体系，实时推送思考增量与工具执行中间步骤
- **HITL 人工确认** — `@Tool(requiresApproval=true)` 高危工具挂起等待审批，`resume()` 恢复执行
- **多厂商适配** — `ModelProvider` 策略接口：OpenAI / DeepSeek（思考链）/ Ollama 本地模型

---

## 快速开始

环境要求：**JDK 21+**、**Maven 3.6+**

### 1. ai-client-sdk — 大模型通信

```xml
<dependency>
    <groupId>com.realapex</groupId>
    <artifactId>ai-client-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

#### 1.1 纯 Java 项目

```java
AiConfig config = AiConfig.builder()
        .apiKeys(List.of("sk-xxxxxxxxx"))
        .model("deepseek-chat")
        .build();
AiClient client = DefaultAiClient.create(config);

// 同步对话
String reply = client.generateText(AiRequest.builder()
        .messages(List.of(Message.user("你好")))
        .build());

// SSE 流式（打字机效果）
AiRequest req = AiRequest.builder()
        .messages(List.of(Message.user("介绍一下 Java 21 新特性")))
        .build();
client.streamText(req, new StreamListener() {
    @Override public void onChunk(String chunk) { System.out.print(chunk); }
    @Override public void onComplete() { System.out.println("\n[完成]"); }
    @Override public void onError(Throwable e) { System.err.println("流式异常: " + e.getMessage()); }
});

// 结构化 JSON 输出
record CheckResult(List<String> errors, int score) {}
CheckResult result = client.generateObject(
        AiRequest.builder()
                .messages(List.of(
                    Message.system("你是校对员，返回 JSON：{\"errors\":[...],\"score\":0-100}"),
                    Message.user("请检查: " + doc)))
                .build(),
        CheckResult.class);
```

#### 1.2 Tool Calling（函数调用）

```java
// 定义工具 Schema
ToolDefinition weatherTool = ToolDefinition.of(
        "get_weather", "查询指定城市的天气",
        Map.of("type", "object",
               "properties", Map.of("city", Map.of("type", "string", "description", "城市名")),
               "required", List.of("city")));

// 发送请求，LLM 决定调用工具
AiResponse response = client.generate(AiRequest.builder()
        .messages(List.of(Message.user("北京今天天气怎么样？")))
        .tools(List.of(weatherTool))
        .toolChoice(ToolChoice.AUTO)
        .build());

// 判断是否有工具调用
if (response.hasToolCalls()) {
    for (ToolCall tc : response.getToolCalls()) {
        System.out.println("LLM 请求调用: " + tc.getName() + "(" + tc.getArguments() + ")");
        // 执行业务逻辑，构造 tool 消息回传
        String toolResult = callWeatherApi(tc.getArguments());
        // 下一轮请求携带 tool 消息...
    }
}
```

#### 1.3 错误处理

```java
try {
    String reply = client.generateText(request);
} catch (RateLimitException e) {
    // SDK 已自动重试 3 次仍失败，需业务侧等待后重试
    Thread.sleep(5000);
} catch (AuthenticationException e) {
    // API Key 无效，检查配置
} catch (AiClientException e) {
    // 其他通信异常
}
```

---

### 2. ai-tool-sdk — 定义工具

```xml
<dependency>
    <groupId>com.realapex</groupId>
    <artifactId>ai-tool-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

#### 2.1 实现 AgentTool 接口（推荐，纯 Java 可用）

```java
public class CalculatorTool implements AgentTool<CalculatorTool.Input, Double> {
    @Override public String name() { return "calculator"; }
    @Override public String description() { return "执行数学表达式计算"; }
    @Override public Class<Input> requestClass() { return Input.class; }

    @Override
    public Double execute(Input req) {
        return eval(req.expression());
    }

    public record Input(
        @ToolParam(description = "数学表达式", required = true) String expression
    ) {}
}

// 使用安全执行包装
ToolResult result = new CalculatorTool().executeSafely(new CalculatorTool.Input("1+1"));
if (result.isSuccess()) {
    System.out.println("结果: " + result.getData());
} else {
    System.out.println("失败: " + result.getError());
}
```

#### 2.2 @Tool 注解（Spring 环境，自动扫描）

```java
@Component
public class MyTools {
    @Tool(name = "get_weather", description = "查询城市天气")
    public String getWeather(@ToolParam(description = "城市名") String city) {
        return weatherService.query(city);
    }
}
// 无需手动注册，ToolBeanPostProcessor 自动发现并注册到 ToolRegistry
```

#### 2.3 生成 JSON Schema（独立使用）

```java
SchemaGenerator generator = new SchemaGenerator();
Map<String, Object> schema = generator.generate(CalculatorTool.Input.class);
// → {"type":"object","properties":{"expression":{"type":"string","description":"数学表达式"}},"required":["expression"]}
```

#### 2.4 安全拦截器

```java
// 三个内置拦截器，按优先级链式执行：
// ParamValidator (优先级10) → DangerousCommandFilter (优先级20) → TimeoutInterceptor (优先级50)

// 危险命令过滤示例——以下输入会被拦截：
// "DROP TABLE users"     → SQL 注入拦截
// "rm -rf /"             → 系统命令拦截
// "../../etc/passwd"     → 路径遍历拦截
// "Runtime.getRuntime()" → 代码注入拦截

// 自定义拦截器
public class MyAuditInterceptor implements ToolSecurityInterceptor {
    @Override public void before(String toolName, Object request) {
        log.info("工具调用审计: {} -> {}", toolName, request);
    }
    @Override public int priority() { return 5; }  // 最先执行
}
```

---

### 3. ai-agent-sdk — 智能体编排

```xml
<dependency>
    <groupId>com.realapex</groupId>
    <artifactId>ai-agent-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

#### 3.1 基础用法

```java
// 创建引擎（单例，线程安全）
AgentRunner agentRunner = new AgentRunner(aiClient, new SchemaGenerator());

// 注册工具
ToolRegistry registry = new ToolRegistry(new SchemaGenerator());
registry.register(new CalculatorTool());

// 运行 Agent
try {
    AgentResult result = agentRunner.run(AgentRequest.builder()
            .systemPrompt("你是一个数学助手，遇到计算时使用 calculator 工具")
            .userPrompt("计算 (123 + 456) × 789")
            .tools(registry.getAll())
            .maxSteps(5)
            .build());

    System.out.println("最终回答: " + result.getFinalText());
    System.out.println("执行步数: " + result.getTotalSteps());
    System.out.println("Token 消耗: " + result.getTotalTokens());
} catch (AgentMaxStepsExceededException e) {
    // 达到 maxSteps 仍未完成，e.getPartialResult() 可获取已有结果
    System.err.println("Agent 在 " + e.getMaxSteps() + " 步内未完成任务");
}
```

#### 3.2 结构化输出

```java
record SlowSqlAnalysis(String rootCause, List<String> suggestions, int severity) {}

AgentResult result = agentRunner.run(
        AgentRequest.builder()
                .systemPrompt("你是 MySQL 专家，分析慢 SQL 并给出优化建议")
                .userPrompt("分析: " + slowSql)
                .tools(List.of(explainTool, indexAdvisorTool))
                .maxSteps(5)
                .build(),
        SlowSqlAnalysis.class);  // ← 指定输出类型

SlowSqlAnalysis analysis = (SlowSqlAnalysis) result.getStructuredOutput();
System.out.println("根因: " + analysis.rootCause());
System.out.println("严重度: " + analysis.severity());
```

#### 3.3 生命周期监听

```java
AgentResult result = agentRunner.run(AgentRequest.builder()
        .userPrompt("...")
        .tools(tools)
        .listener(new AgentEventListener() {
            @Override public void onStepStart(int step) {
                System.out.println("--- 第 " + step + " 步开始 ---");
            }
            @Override public void onToolStart(String name, String args) {
                System.out.println("调用工具: " + name + "(" + args + ")");
            }
            @Override public void onToolEnd(String name, Object result) {
                System.out.println("工具返回: " + result);
            }
            @Override public void onStepFinish(AgentStepResult stepResult) {
                System.out.println("步耗时: " + stepResult.getDurationMs() + "ms");
            }
        })
        .build());
```

#### 3.4 流式 Agent（实时推送思考与工具执行中间步骤）

```java
AgentResult result = agentRunner.run(AgentRequest.builder()
        .userPrompt("分析项目代码结构并给出优化建议")
        .tools(tools)
        .streamListener(new AgentStreamListener() {
            @Override public void onThoughtChunk(String chunk) {
                System.out.print(chunk);            // 思考增量（打字机效果）
            }
            @Override public void onToolCallStart(String callId, String name, String arguments) {
                System.out.println("\n[工具] " + name + "(" + arguments + ")");
            }
            @Override public void onToolCallResult(String callId, String name, Object result) {
                System.out.println("[结果] " + result);
            }
            @Override public void onFinalResult(String text, Object structuredOutput) {
                System.out.println("\n[完成] " + text);
            }
        })
        .build());
```

#### 3.5 HITL 人工确认（高危工具挂起/恢复）

```java
// 1. 标记高危工具：@Tool(name = "exec_command", requiresApproval = true)

// 2. 运行 Agent —— 遇到高危工具时抛出 AgentSuspendedException
try {
    agentRunner.run(AgentRequest.builder()
            .userPrompt("执行 mvn clean install 并汇报结果")
            .tools(tools)
            .build());
} catch (AgentSuspendedException e) {
    String suspendId = e.getSuspendId();            // 挂起 ID，用于恢复
    AgentState state = e.getAgentState();           // 挂起快照（messages、pendingToolCalls）

    // 3. 人工审批后恢复执行
    agentRunner.resume(suspendId, ApprovalResult.approve("ops-admin", "确认安全，允许执行"));
    // 或拒绝：agentRunner.resume(suspendId, ApprovalResult.reject("ops-admin", "命令不在白名单"));
}
```

#### 3.6 Spring Boot 自动装配

`application.yml`：

```yaml
ai:
  sdk:
    api-keys:
      - sk-xxxxxxxxx1
      - sk-xxxxxxxxx2
    model: deepseek-chat
    timeout: 60s
    max-retries: 3
  agent:
    max-steps: 10
    max-context-tokens: 8000
```

```java
@RestController
public class AgentController {
    @Autowired private AgentRunner agentRunner;
    @Autowired private ToolRegistry toolRegistry;

    @GetMapping("/ask")
    public String ask(@RequestParam String question) {
        return agentRunner.run(AgentRequest.builder()
                .userPrompt(question)
                .tools(toolRegistry.getAll())
                .build()).getFinalText();
    }
}
```

---

## ReAct 循环流程

```
用户输入 "北京天气如何？"
  │
  ▼
┌─ Step 1 ───────────────────────────────────────┐
│ LLM 思考 → 决定调用 get_weather(city="北京")     │
│ 并行执行工具 → API 返回 "晴，25°C"                │
│ 工具结果回传 messages                            │
│ ContextTrimmer 检查 Token → 无需裁剪              │
└────────────────────────────────────────────────┘
  │
  ▼
┌─ Step 2 ───────────────────────────────────────┐
│ LLM 收到工具结果 → 组织自然语言                   │
│ finish_reason=stop → 无更多 tool_calls           │
│ 返回最终文本："北京今天晴天，气温 25°C"            │
└────────────────────────────────────────────────┘
  │
  ▼
AgentResult { finalText, totalSteps=2, totalUsage, stepResults }
```

---

## 配置参考

### ai-client-sdk (`ai.sdk.*`)

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `base-url` | `https://api.deepseek.com/v1` | 兼容 OpenAI 的 API 端点 |
| `api-keys` | —（必填） | API Key 列表，Round-Robin 轮询，401/402 自动隔离 10 分钟 |
| `model` | `deepseek-chat` | 默认模型 |
| `provider` | `openai` | 厂商策略：`openai` / `deepseek` / `ollama`（或 `vllm` / `local`） |
| `timeout` | `60s` | 总请求超时 |
| `connect-timeout` | `10s` | TCP 连接超时 |
| `read-timeout` | `30s` | SSE 流式无数据包最大等待间隔 |
| `max-retries` | `3` | 429/5xx 自动重试次数 |
| `retry-base-delay` | `1s` | 重试指数退避基础延迟 |
| `key-blacklist-duration` | `10m` | Key 故障隔离时长（401/402） |

### ai-agent-sdk (`ai.agent.*`)

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `max-steps` | `10` | ReAct 循环最大步数，超步抛出 AgentMaxStepsExceededException |
| `max-context-tokens` | `8000` | 触发上下文裁剪的 Token 阈值 |
| `model` | — | 覆盖 ai-client-sdk 的默认模型 |

---

## 项目结构

```
ai-workbench/
 ├── README.md              <-- 快速上手（本文件）
 ├── 能力清单.md            <-- 全量能力清单与迭代路线
 ├── pom.xml                <-- 父 POM
 ├── ai-client-sdk/         <-- 通信基座
 ├── ai-tool-sdk/           <-- 工具基座
 ├── ai-agent-sdk/          <-- 编排引擎
 ├── ai-tool-doc/           <-- 领域工具包：文档转换/模板渲染
 └── ai-tool-db/            <-- 领域工具包：数据库工具（Schema/查询/EXPLAIN/慢日志/受控写）
```

## 构建

```bash
git clone ...
cd ai-workbench
mvn clean install -DskipTests    # 一次性构建全部模块
```

> API 详细文档见各模块源码 **Javadoc**。发布 Jar 附带 `-sources.jar`，IDE 自动读取 Javadoc 提供参数提示与异常处理建议。
