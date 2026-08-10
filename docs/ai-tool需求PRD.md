这是一个**非常敏锐且具备高度架构前瞻性**的拆分思路！

在大型企业级或平台级框架的演进过程中，把工具层独立抽离为 **`ai-tool-sdk`**（甚至作为 `ai-workbench` 矩阵中的标准工具基座），不仅合理，而且能**完美解决解耦、安全隔离与生态复用**的问题。

---

## 一、 为什么必须独立出 `ai-tool-sdk`？

如果不独立，把 Tool 散落在 `ai-agent-sdk` 或各自的业务工程里，很快就会遇到以下 3 个工程痛点：

```
┌────────────────────────────────────────────────────────────────────────┐
│                        应用层 / 业务系统                              │
└──────────────┬──────────────────────────┬──────────────────────────────┘
               │ 依赖                      │ 依赖
               ▼                          ▼
┌──────────────────────────────┐   ┌─────────────────────────────────────┐
│        ai-agent-sdk          │   │             ai-tool-sdk             │
│ (ReAct 状态机 / 线程并发驱动) │   │ (工具定义契约 / 基础工具箱 / 沙箱)   │
└──────────────┬───────────────┘   └──────────────────┬──────────────────┘
               │                                      │
               │           单向依赖 ai-tool-sdk        │
               └──────────────────────────────────────┘

```

1. **依赖重合与膨胀问题**：
* 某些基础 Tool（如 `PythonCodeInterpreter`、`DocumentParserTool`、`WebSearchTool`）可能会引入重量级依赖（如 Python JNI/GRPC 客户端、PDF 解析库、HTTP 爬虫库）。如果把这些直接塞进 `ai-agent-sdk`，会导致只需要纯 ReAct 状态机的轻量级应用承受巨大的 Jar 包体积。


2. **“工具即服务” (Tool as a Service) 跨语言/跨系统复用**：
* `ai-tool-sdk` 定义的标准工具契约，不仅可以被 `ai-agent-sdk` 调用，未来甚至可以脱离 Agent，直接被应用层 API 调用，或者暴露给前端、工作流引擎（如 Flowable / LangChain）使用。


3. **安全与沙箱隔离 (Sanitization & Sandbox)**：
* 工具的执行（特别是代码执行、高危命令执行、SQL 执行）是整个 AI 架构中最容易发生安全漏洞的地方。独立出 `ai-tool-sdk`，可以统一把参数校验、权限鉴权、敏感词过滤、运行沙箱（Docker/Wasm/SecManager）集中管理。



---

## 二、 `ai-tool-sdk` 的核心概念与定位

| 维度 | `ai-tool-sdk` (工具契约与基础能力库) |
| --- | --- |
| **一句话定位** | **“AI 基础设施工具契约、标准工具箱与安全沙箱执行器”** |
| **解决的核心问题** | 如何标准化地定义、校验、安全执行一个 Tool，并提供通用的开箱即用工具？ |
| **与 Agent 的关系** | **被依赖关系**。`ai-agent-sdk` 依赖 `ai-tool-sdk` 提供的契约去生成 Schema 和执行反射；`ai-agent-sdk` 不关心 Tool 内部是怎么算出来的。 |
| **代码执行与安全** | **核心阵地**。负责具体的 Java 反射、Python 跨语言调用、Shell 隔离、入参 JSON Schema 强校验。 |

---

## 三、 `ai-tool-sdk` 的内部架构设计

建议将 `ai-tool-sdk` 设计为 **“1 个 Core 契约 + N 个 Tool 扩展包”** 的结构，保证“按需引入”：

```
ai-tool-sdk/
├── ai-tool-core                   # 核心契约与安全沙箱 (必选)
│   ├── annotation/                # @Tool, @ToolParam 注解
│   ├── contract/                  # AgentTool<REQ, RESP> 接口、ToolResult 标准返回
│   ├── schema/                    # 基于 Jackson 的 Schema 自动生成器
│   └── security/                  # 入参校验器、超时拦截器、敏感词/高危指令过滤器
│
├── ai-tool-builtin-calc           # [可选] 算力与代码类工具
│   ├── CalculatorTool             # 表达式/高精度计算器
│   └── CodeInterpreterTool        # 沙箱代码解释器 (Python/Groovy)
│
├── ai-tool-builtin-search         # [可选] 检索与网络类工具
│   ├── WebSearchTool              # 谷歌/Bing 联网搜索
│   └── HttpFetchTool              # Safe HTTP 抓取与 HTML 转 Markdown
│
└── ai-tool-builtin-system         # [可选] 系统与人机协作工具
    ├── HumanInTheLoopTool         # 人工审批挂起工具
    └── ShellCommandTool           # 受限系统 Shell 命令工具

```

---

## 四、 `ai-tool-sdk` 带来的标准开发体验

独立出 `ai-tool-sdk` 后，开发者定义一个工具或使用工具将变得极度优雅：

### 1. 开发者如何用 `ai-tool-sdk` 自定义工具？

```java
@Tool(
    name = "execute_mysql_query", 
    description = "执行只读 SQL 查询并返回 JSON 结果",
    readOnly = true // 安全属性标记
)
public class MysqlQueryTool implements AgentTool<MysqlQueryTool.Request, MysqlQueryTool.Response> {

    @Override
    public Response execute(Request request) {
        // 1. 安全校验 (由 ai-tool-core 拦截器或 Tool 内部完成)
        if (request.sql().toLowerCase().contains("drop")) {
            throw new SecurityException("禁止执行高危 DDL 语句");
        }
        // 2. 执行业务逻辑
        List<Map<String, Object>> rows = dbClient.query(request.sql());
        return new Response(rows);
    }

    public record Request(
        @ToolParam(description = "待执行的只读 SQL 语句", required = true) 
        String sql
    ) {}

    public record Response(List<Map<String, Object>> data) {}
}

```

### 2. `ai-agent-sdk` 如何无缝挂载？

```java
// Agent SDK 只需要把 ai-tool-sdk 扫描到的 Tool 丢进 Registry 即可
ToolRegistry registry = new ToolRegistry();
registry.register(new MysqlQueryTool());
registry.register(new CalculatorTool()); // 来自 ai-tool-builtin-calc

AgentRunner runner = AgentRunner.builder()
        .aiClient(aiClient)
        .toolRegistry(registry) // 自动绑定 Schema 并驱动 ReAct 循环
        .build();

```

---

## 五、 三大 SDK 的最终合体蓝图 (The AI Workbench Trinity)

至此，你的 AI 基础框架底层“铁三角”就完全清晰了：

1. **`ai-client-sdk`**：**协议传输基座**（负责 HTTP、SSE 流式、API Key 轮询、Token 计费、多模型 Vendor 适配）。
2. **`ai-tool-sdk`**：**工具与安全基座**（负责 `@Tool` 契约定义、JSON Schema 解析、内置工具箱、安全沙箱拦截）。
3. **`ai-agent-sdk`**：**状态机与编排引擎**（负责 ReAct `while` 循环、多工具并发调度、上下文剪裁、人机交互中断恢复）。

这样拆分后，职责边界极其严密，层层单向依赖，没有任何死锁和循环引用，完全具备支撑大型企业级 AI 平台和 Agent 矩阵的能力！