以下是为你量身定制的 **`ai-agent-sdk` 需求 PRD（产品需求文档）**。

这份 PRD 延续了你的整体架构规范：**基于 Java 21（虚拟线程）、纯粹无状态通用原语、零重型 AI 框架依赖**，并与已有的 `ai-client-sdk` 形成清晰的解耦与上下游依赖关系。

---

# 产品需求文档 (PRD)：`ai-agent-sdk` 通用智能体编排 SDK

| 文档版本 | 创建日期 | 状态 | 依赖模块 | 技术基线 |
| --- | --- | --- | --- | --- |
| **v1.0.0** | 2026-08-10 | 📋 需求评审 | `ai-client-sdk` (v1.0.0+) | Java 21 / Spring Boot 3.x (可选) |

---

## 1. 产品定位与目标

### 1.1 产品定位

`ai-agent-sdk` 是 `ai-workbench` 矩阵工程中的 **ReAct 智能体状态机与工具编排 SDK**。它处于业务应用与底层 `ai-client-sdk` 之间，专注于提供无状态的 ReAct（Thought-Action-Observation）循环驱动引擎、多工具并发调度、上下文 Token 动态剪裁以及生命周期事件观察能力。

### 1.2 设计原则

* **单一职责（Single Responsibility）**：绝不介入任何具体业务场景（如慢 SQL 分析、监控排查等业务工具逻辑完全交由应用层编写）。
* **轻量高吞吐（Lightweight & High-Throughput）**：基于 Java 21 虚拟线程（Virtual Threads）实现并发 Tool 调用与调度，不引入 LangChain4j / Spring AI 等重型框架。
* **单向依赖（Unidirectional Dependency）**：强依赖 `ai-client-sdk` 进行底层 LLM 报文交互，自身不包含任何 HTTP 通信与 API Key 轮询逻辑。

---

## 2. 系统架构与依赖关系

```
┌────────────────────────────────────────────────────────┐
│               应用层 (Application Business)             │
│   • 业务 Tool 实现 (@Tool / AgentTool)                  │
│   • 业务 Prompt 编写 & 跨会话持久化 (Redis/DB)          │
└───────────────────────────┬────────────────────────────┘
                            │ 依赖
                            ▼
┌────────────────────────────────────────────────────────┐
│                   ai-agent-sdk (本 SDK)                │
│ ┌──────────────────┐  ┌──────────────────────────────┐ │
│ │ AgentRunner      │  │ ToolRegistry & Schema        │ │
│ │ (ReAct Loop)     │  │ (Jackson Schema Generator)   │ │
│ └──────────────────┘  └──────────────────────────────┘ │
│ ┌──────────────────┐  ┌──────────────────────────────┐ │
│ │ ContextTrimmer   │  │ AgentEventListener           │ │
│ │ (Token 裁剪/窗口)│  │ (生命周期 Hook / SSE)        │ │
│ └──────────────────┘  └──────────────────────────────┘ │
└───────────────────────────┬────────────────────────────┘
                            │ 依赖 (单向)
                            ▼
┌────────────────────────────────────────────────────────┐
│             ai-client-sdk (基础通信 SDK)                │
│   • AiClient / HttpClient (Java 21 Virtual Threads)    │
│   • Message / ToolDefinition / ToolCall 数据模型       │
└────────────────────────────────────────────────────────┘

```

---

## 3. 核心功能需求 (Functional Requirements)

### 3.1 模块 1：工具契约与 Schema 引擎 (`tool` 包)

| 需求编号 | 功能点 | 详细说明 | 优先级 |
| --- | --- | --- | --- |
| **FR-TOOL-01** | **`AgentTool<REQ, RESP>` 泛型接口** | 提供统一的工具契约接口，包含 `name()`、`description()`、`requestClass()` 和 `execute(REQ)` 方法。 | P0 |
| **FR-TOOL-02** | **`SchemaGenerator` 工具类** | 基于 Jackson 及 Java 反射，自动将 Java `Record` / DTO 的 Class 结构转换为 OpenAI 兼容的 JSON Schema 格式 (`ToolDefinition`)。 | P0 |
| **FR-TOOL-03** | **`@Tool` 注解支持** | 提供 `@Tool(description = "...")` 注解，允许开发人员直接在 Spring Bean 的普通 Java 方法上标注，自动提取方法参数作为 Schema。 | P1 |
| **FR-TOOL-04** | **`ToolRegistry` 注册表** | 提供线程安全的工具注册表，支持按 `tool_name` 进行路由查找与动态挂载。 | P0 |

---

### 3.2 模块 2：ReAct 循环与并行执行引擎 (`execution` 包)

| 需求编号 | 功能点 | 详细说明 | 优先级 |
| --- | --- | --- | --- |
| **FR-EXEC-01** | **`AgentRunner` ReAct 驱动器** | 实现 `while (steps < maxSteps)` 循环。负责发起 `ai-client-sdk` 请求、解析返回的 `ToolCall` 指令、调用本地 Tool、将结果拼装为 `Message.toolResult()` 并发起下一轮思考。 | P0 |
| **FR-EXEC-02** | **多工具并发调度 (Parallel Tool Calls)** | 当 LLM 单次返回多个 `ToolCall` 时，**利用 Java 21 虚拟线程 (`Executors.newVirtualThreadPerTaskExecutor()`)** 并行执行 `Tool.execute()`，显著降低总体等待延迟。 | P0 |
| **FR-EXEC-03** | **工具执行错误自愈** | 当 `Tool.execute()` 抛出异常（如数据库超时、参数非法）时，SDK 需捕获 Exception 并将堆栈提示包装为 `toolResult` 喂回 LLM，驱动 LLM 自行修正参数重试。 | P1 |
| **FR-EXEC-04** | **`maxSteps` 熔断保护** | 达到最大步数依然未输出最终文本时，抛出 `AgentMaxStepsExceededException`，防止 LLM 进入无线调工死循环。 | P0 |
| **FR-EXEC-05** | **结构化 Agent 结果导出 (`generateObject`)** | 支持调用方传入 `Class<T>` 作为 Agent 的最终输出形态。当 Agent 完成 `maxSteps` 或 LLM 返回终止信号（`finish_reason=stop` 且无 tool_calls）时，SDK 在最后一步自动调用 `ai-client-sdk` 进行结构化反序列化（Structured Output / JSON Mode），将最终文本映射为指定的强类型 Java 对象返回，避免应用层手动解析 LLM 自然语言输出。核心签名：`<T> AgentResult<T> run(AgentRequest request, Class<T> outputClass)`。 | P1 |

---

### 3.3 模块 3：上下文管理与 Token 动态裁剪 (`context` 包)

| 需求编号 | 功能点 | 详细说明 | 优先级 |
| --- | --- | --- | --- |
| **FR-CTX-01** | **`ContextTrimmer` 上下文裁剪器** | 提供按 `maxTokens` 或滑动窗口策略裁剪历史 `messages` 的能力。<br><br>**⚠️ Tool Message 剪裁配对规则**：在裁剪时必须保证消息序列的协议完整性——若某条 `tool` role 消息（toolResult）因超出窗口被剪裁，则**必须同步剪裁其对应的 `assistant` role 消息中的 `tool_calls`**（即包含该 `tool_call_id` 的那条 assistant 消息）。否则 OpenAI / 智谱等严格校验消息序列协议的厂商会直接返回 HTTP 400 `Invalid messages sequence: a tool result must follow a tool call`。 | P1 |
| **FR-CTX-02** | **核心消息保护机制** | 裁剪时**强制保护** `system` 消息与最新的 `user` 问题不被切掉，优先裁剪或截断历史冗长的 `toolResult` 数据（如过长日志）。 | P1 |

---

### 3.4 模块 4：生命周期 Hook 与观察者模式 (`event` 包)

| 需求编号 | 功能点 | 详细说明 | 优先级 |
| --- | --- | --- | --- |
| **FR-EVT-01** | **`AgentEventListener` 回调接口** | 暴露生命周期钩子，便于应用层推播前端（SSE/WebSocket）与轨迹持久化：<br>

<br>• `onStepStart(int step)` — 每一步 ReAct 循环开始<br>

<br>• `onStepFinish(AgentStepResult stepResult)` — 每一步 ReAct 循环结束，携带本步完整上下文（当前步数、LLM 原始返回、Tool 调用与结果列表、本步 Token 消耗），**推荐应用层在此回调中做轨迹持久化（落库/Redis）与前端 SSE 增量推播**<br>

<br>• `onToolStart(String toolName, String args)` — 单个 Tool 调用开始<br>

<br>• `onToolEnd(String toolName, Object result)` — 单个 Tool 调用结束<br>

<br>• `onChunk(String textChunk)` — 打字机流式增量推送<br>

<br>• `onComplete(AgentResult result)` — Agent 整体执行完成 | P0 |
| **FR-EVT-02** | **`AgentResult` 统计元数据** | 执行完成时，返回包含 `finalText`、`totalSteps`、`totalTokens` (来自 `ai-client-sdk` 的 `Usage`) 和总耗时的结果对象。 | P0 |

---

### 3.5 模块 5：Spring Boot 自动装配集成 (`starter`)

| 需求编号 | 功能点 | 详细说明 | 优先级 |
| --- | --- | --- | --- |
| **FR-SPT-01** | **注解扫描器 (`ToolBeanPostProcessor`)** | 启动时自动扫描 Spring 上下文中所有带有 `@Tool` 注解的 Bean 和 `AgentTool` 实例，自动注入到全局 `ToolRegistry`。 | P1 |
| **FR-SPT-02** | **`AgentRunner` 自动装配** | 提供 `ai-agent-spring-boot-starter`，开箱即用自动注入 `AgentRunner` 单例。 | P1 |

---

## 4. API 交互与使用范例 (Developer Experience)

### 4.1 应用层定义 Tool (实现 `AgentTool` 或使用 `@Tool`)

```java
// 方式 A：实现泛型接口
@Component
public class GetExplainPlanTool implements AgentTool<ExplainRequest, String> {
    @Autowired private JdbcTemplate jdbcTemplate;

    @Override public String name() { return "get_explain_plan"; }
    @Override public String description() { return "获取指定 SQL 的 EXPLAIN 执行计划"; }
    @Override public Class<ExplainRequest> requestClass() { return ExplainRequest.class; }

    @Override
    public String execute(ExplainRequest req) {
        return jdbcTemplate.queryForList("EXPLAIN " + req.sql()).toString();
    }
}

// 方式 B：使用 @Tool 注解
@Component
public class MonitoringTools {
    @Tool(description = "根据指标名称查询 Prometheus 近 10 分钟打点")
    public String queryMetric(String metricName) {
        return prometheusClient.query(metricName);
    }
}

```

### 4.2 业务 Service 驱动 AgentRunner

```java
@Service
public class SlowSqlAnalysisService {

    @Autowired private AgentRunner agentRunner; // 来自 ai-agent-sdk
    @Autowired private GetExplainPlanTool explainPlanTool;

    public String analyzeSlowSql(String sql) {
        AgentRequest request = AgentRequest.builder()
                .systemPrompt("你是资深 MySQL DB 专家...")
                .userPrompt("分析慢 SQL 瓶颈: " + sql)
                .addTool(explainPlanTool)
                .maxSteps(5)
                .listener(new AgentEventListener() {
                    @Override
                    public void onToolStart(String name, String args) {
                        System.out.println("👉 Agent 正在调用工具 [" + name + "]，参数: " + args);
                    }
                })
                .build();

        AgentResult result = agentRunner.run(request);
        System.out.println("消耗总 Token: " + result.getUsage().getTotalTokens());
        return result.getFinalText();
    }
}

```

---

## 5. 非功能性需求 (Non-Functional Requirements)

1. **高性能与低开销**：
* SDK 本身内存占用维持在 MB 级别；
* Tool 多路并发执行必须使用 Java 21 虚拟线程，不得占用传统操作系统 OS 线程池。


2. **健壮性与死循环防御**：
* 必须严格受控于 `maxSteps` 和 Timeout 限制。
* 单个 Tool 执行失败不得导致整个 Java 进程崩溃或未捕获的 Unhandled Exception。


3. **无状态与可扩展**：
* `AgentRunner` 实例必须是线程安全的单例（Singleton），所有多轮对话状态必须限制在单次 `run(AgentRequest)` 请求作用域（Request-scoped/Local variable）内。



---

## 6. 项目模块目录规划

```
ai-workbench/
 ├── ai-client-sdk/            <-- [已完成] 底层通信 SDK
 ├── ai-agent-sdk/             <-- [本次开发] Agent 编排 SDK
 │    ├── annotation/          <-- @Tool 注解定义
 │    ├── context/             <-- ContextTrimmer 裁切逻辑
 │    ├── event/               <-- AgentEventListener & AgentResult
 │    ├── execution/           <-- AgentRunner & Virtual Thread Executor
 │    ├── tool/                <-- AgentTool, SchemaGenerator, ToolRegistry
 │    └── config/              <-- Spring Boot AutoConfiguration
 ├── ai-document-parser-sdk/   <-- [待规划]
 └── ai-template-engine-sdk/   <-- [待规划]

```

---

## 7. 开发里程碑与交付计划 (Roadmap)

* **Phase 1（核心 ReAct 引擎）**：
* 实现 `AgentTool` 接口 + `SchemaGenerator` (基于 Jackson)。
* 实现纯 Java 版 `AgentRunner`（支持单线程与虚拟线程并发执行 ToolCall）。


* **Phase 2（上下文与观察者）**：
* 实现 `ContextTrimmer` 上下文剪裁。
* 实现 `AgentEventListener` 打印与事件回调。


* **Phase 3（Spring Boot 集成）**：
* 实现 `@Tool` 注解扫描器与 `AutoConfiguration`。
* 编写单元测试与应用层集成 Demo。