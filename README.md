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

#### 1.4 上下文持久化（Trace 自动落盘）

`ai-client-sdk` 内置通用调用日志与 Trace 持久化能力，将每次 LLM 调用的 **请求/响应全量上下文**、Token 统计、状态机（`INIT→RUNNING→STREAMING→SUCCESS/FAILED`）与 **TTFT 首 Token 延迟** 自动写入应用侧 PostgreSQL 的 `sys_llm_invoke_log` 表，开发者零手动建表成本。

##### Spring Boot 接入（两处配置 + 声明业务上下文）

```yaml
ai:
  client:
    trace:
      enabled: true             # 开启通用日志记录
      auto-ddl: true            # 启动时若无 sys_llm_invoke_log 表则自动建表
      max-payload-length: 50000 # 单条日志 request/response JSON 最大字符（超长截断）
```

应用侧 Flyway 可在 `locations` 追加 SDK 固化的脚本目录，由应用统一调度 DDL（推荐）：
```yaml
spring:
  flyway:
    locations:
      - classpath:db/migration      # 应用侧原有脚本
      - classpath:db/sdk-migration  # SDK 固化脚本（V1.0.0_1__create_sys_llm_invoke_log.sql）
```

业务代码入口声明上下文，剩余全部自动完成：
```java
@PostMapping("/analyze")
public SseEmitter analyzeDatabase(@RequestBody DBAnalysisRequest request) {
    // 1. 设置 Trace 上下文（场景类型、会话 ID）
    LLMTraceContext.set(TraceContext.builder()
            .sceneType("DB_ANALYSIS")
            .sessionId(request.getSessionId())
            .build());

    try {
        // 2. 直接调用 Agent（拦截器自动落盘请求/响应/Tokens/TTFT）
        return dbAgentService.runStreamAnalysis(request);
    } finally {
        // 3. 清理上下文（防线程池泄漏）
        LLMTraceContext.clear();
    }
}
```

##### 纯 Java 接入（手动装配，框架无关）

```java
// 复用应用侧已配置的 DataSource（推荐复用连接池，不新建数据库）
AsyncLLMLogStorageService storage = new AsyncLLMLogStorageService(dataSource, "sys_llm_invoke_log", 2);
LLMInvokeTraceInterceptor interceptor = new LLMInvokeTraceInterceptor(storage, props);

AiClient client = DefaultAiClient.create(AiConfig.builder()
        .apiKeys(List.of("sk-xxx"))
        .traceInterceptor(interceptor)   // 注入后每次调用自动落盘
        .build());

// 异步场景跨线程透传上下文
TraceContext ctx = LLMTraceContext.get();
CompletableFuture<AiResponse> future = CompletableFuture.supplyAsync(() ->
        LLMTraceContext.wrap(ctx, () -> client.generate(request)));
```

##### 表结构要点

- 全量上下文存于 `request_payload` / `response_payload` 两个 **JSONB** 字段（System Prompt、多轮 Messages、Tools、推理链、ToolCall 结果）。
- 异步状态机、`trace_id` / `parent_log_id` 用于 Agent 子任务树状串联。
- 内置 `created_at` 索引，便于应用侧定期清理/冷热分离大 JSONB。

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

### 4. ai-tool-doc — 文档转换/模板渲染

```xml
<dependency>
    <groupId>com.realapex</groupId>
    <artifactId>ai-tool-doc</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

#### 4.1 工具清单

| 工具名 | 说明 |
|---|---|
| `read_and_convert_doc` | 文档 → Markdown（Word/Excel/PDF，支持本地路径 / http(s) URL / Base64） |
| `inspect_template_schema` | 模板占位符探查（.docx/.xlsx），输出字段清单供 Agent 准备渲染数据 |
| `render_document` | 数据 → 模板渲染（poi-tl 渲染 Word、EasyExcel 渲染 Excel） |

#### 4.2 纯 Java 用法

```java
// 1. 构建配置（沙箱根目录 + 输出目录）
DocToolConfig config = DocToolConfig.builder()
        .baseDir(Path.of("/home/workspace/docs"))
        .outputDir(Path.of("/home/workspace/docs/output"))
        .build();

// 2. 一键挂载 3 个文档原子工具
List<AgentTool<?, ?>> docTools = DocToolFactory.createDocTools(config);

// 3. 领域专属路径拦截器（优先级 5，先于通用链执行）
ToolSecurityInterceptor interceptor = DocToolFactory.createPathInterceptor(config);

// 4. 直接调用：Word → Markdown
ToolResult result = docTools.get(0).executeSafely(
        new DocConvertRequest("/home/workspace/docs/report.docx", null, null, null));
if (result.isSuccess()) {
    DocConvertResult data = (DocConvertResult) result.getData();
    System.out.println(data.getMarkdown());
}
```

#### 4.3 Spring Boot 自动装配

`application.yml`：

```yaml
realapex:
  tool:
    doc:
      base-dir: /home/workspace/docs
      output-dir: /home/workspace/docs/output
      max-doc-size-bytes: 20971520
      max-output-chars: 20000
      max-pages: 50
      max-rows: 100
      max-cols: 50
      extract-images: true
      auto-upgrade-doc: true
      render-requires-approval: false
      timeout-ms: 30000
```

自动注册 Bean：`docToolConfig`（配置）、`docTools`（3 个工具）、`documentPathInterceptor`（路径拦截器）。

#### 4.4 安全特性

- **路径沙箱**：`DocumentPathInterceptor`（优先级 5）校验输入/输出路径，禁止 `../` 穿越与沙箱外逃逸
- **大小卡口**：文档 ≤ 20MB、PDF/Word ≤ 50 页、Excel ≤ 100 行 × 50 列（防 OOM）
- **输出截断**：单工具返回 ≤ 20,000 字符（复用 `OutputTruncator`）
- **HITL 可选**：`render-requires-approval: true` 时渲染操作触发人工审批

---

### 5. ai-tool-db — 数据库工具

```xml
<dependency>
    <groupId>com.realapex</groupId>
    <artifactId>ai-tool-db</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

#### 5.1 工具清单

| 工具名 | 说明 |
|---|---|
| `get_db_schema` | Schema 探查（表/字段/索引/主外键/注释，带缓存） |
| `readonly_query` | 只读查询（SELECT/SHOW/DESCRIBE/EXPLAIN，语法级只读校验） |
| `explain_sql` | 执行计划诊断（EXPLAIN，输出计划行） |
| `fetch_slow_logs` | 慢查询抓取（MySQL `mysql.slow_log` / GaussDB `dbe_perf.statement_history`） |
| `execute_update` | 受控写操作（仅 INSERT/UPDATE/DELETE，触发 HITL 人工审批） |

#### 5.2 双模式接入

```java
// 模式 A：外部注入 DataSource（推荐，生命周期归应用管理）
List<AgentTool<?, ?>> dbTools = DbToolFactory.createDbTools(
        DbToolConfig.builder().dataSource(ds).build());

// 模式 B：工具包托管连接池（TTL 空闲自动销毁）
DbConnectionManager manager = new DbConnectionManager(null);
manager.register("test-db", DbToolConfig.builder()
        .jdbcUrl("jdbc:mysql://localhost:3306/demo")
        .username("root")
        .password("secret")
        .build());
List<AgentTool<?, ?>> dbTools = DbToolFactory.createDbTools(
        DbToolConfig.builder()
                .dataSource(manager.resolve("test-db"))
                .build());
```

#### 5.3 直接调用示例

```java
ToolResult result = dbTools.get(1).executeSafely(
        new QueryRequest("SELECT id, name FROM user WHERE status = 1", null));
if (result.isSuccess()) {
    QueryResult data = (QueryResult) result.getData();
    System.out.println("列: " + data.columns());
    System.out.println("行数: " + data.rowCount());
}
```

#### 5.4 与 Agent 集成（HITL 写审批）

```java
ToolRegistry registry = new ToolRegistry(new SchemaGenerator());
dbTools.forEach(registry::register);

// execute_update 标记 requiresApproval=true：
// AgentRunner 执行到写操作时抛出 AgentSuspendedException，
// 人工审批后 agentRunner.resume(suspendId, ApprovalResult.approve(...)) 恢复执行
```

#### 5.5 Spring Boot 自动装配

`application.yml`：

```yaml
realapex:
  tool:
    db:
      jdbc-url: jdbc:mysql://localhost:3306/mydb
      username: root
      password: ${DB_PASSWORD}
      dialect: mysql          # mysql / tdsql / gaussdb，留空自动探测
      maximum-pool-size: 5
      minimum-idle: 1
      connection-timeout-ms: 5000
      idle-timeout-ms: 60000
      max-lifetime-ms: 1800000
      keepalive-time-ms: 0
      idle-ttl-minutes: 30
      max-output-chars: 20000
      query-timeout-seconds: 10
      max-rows: 100
      max-affected-rows: 500
```

自动注册 Bean：`dbToolConfig`（配置）、`dbTools`（5 个工具）、`readOnlySqlInterceptor`（只读拦截器）、`dbConnectionManager`（动态多数据源管理器）。

#### 5.6 安全体系

- **只读拦截**：`ReadOnlySqlInterceptor`（优先级 5）基于 JSqlParser 语法级解析，白名单 SELECT/SHOW/DESCRIBE/EXPLAIN，多语句（`;`）直接拦截
- **写操作卡口**：仅允许 INSERT/UPDATE/DELETE；UPDATE/DELETE 必须带 WHERE；影响行数 ≤ 500
- **方言防护**：TDSQL 分片键校验（shard_key/user_id 等）、GaussDB 高危函数检测
- **超时强杀**：语句级 10s 超时；只读查询强制 LIMIT 100 行
- **输出截断**：单工具返回 ≤ 20,000 字符
- **HITL 审批**：`execute_update` 必须人工确认后才执行

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

### ai-tool-doc (`realapex.tool.doc.*`)

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `base-dir` | 当前工作目录 | 沙箱根目录（输入/输出路径统一限定） |
| `output-dir` | `base-dir/output` | 渲染输出目录 |
| `temp-dir` | `base-dir/temp` | URL/Base64 下载临时目录 |
| `max-doc-size-bytes` | `20971520` | 输入文档大小上限（20MB） |
| `max-output-chars` | `20000` | 返回结果截断上限 |
| `max-pages` | `50` | PDF/Word 最大解析页数 |
| `max-rows` | `100` | Excel 最大解析行数 |
| `max-cols` | `50` | Excel 最大解析列数 |
| `extract-images` | `true` | 是否提取文档内图片 |
| `auto-upgrade-doc` | `true` | .doc 自动升级为 .docx |
| `render-requires-approval` | `false` | 渲染是否触发 HITL 审批 |
| `timeout-ms` | `30000` | 下载/解析超时（毫秒） |

### ai-tool-db (`realapex.tool.db.*`)

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `jdbc-url` | — | 模式 B：JDBC URL（如 `jdbc:mysql://localhost:3306/mydb`） |
| `username` / `password` | — | 模式 B：数据库账号密码 |
| `dialect` | 自动探测 | 方言：`mysql` / `tdsql` / `gaussdb` |
| `maximum-pool-size` | `5` | 连接池最大连接数（Agent 专用小池子） |
| `minimum-idle` | `1` | 连接池最小空闲连接数 |
| `connection-timeout-ms` | `5000` | 获取连接超时（毫秒） |
| `idle-timeout-ms` | `60000` | 空闲回收超时（毫秒） |
| `max-lifetime-ms` | `1800000` | 连接最大生命周期（毫秒，30 分钟） |
| `keepalive-time-ms` | `0` | keepalive 时间（TDSQL 建议 30000） |
| `idle-ttl-minutes` | `30` | 托管连接池空闲驱逐 TTL（分钟） |
| `max-output-chars` | `20000` | 返回结果截断上限 |
| `query-timeout-seconds` | `10` | 语句级查询超时（秒） |
| `max-rows` | `100` | 只读查询最大返回行数 |
| `max-affected-rows` | `500` | 写操作影响行数上限 |

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
