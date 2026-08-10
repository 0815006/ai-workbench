# AI Workbench SDK

轻量级、低耦合的 AI 辅助开发 SDK 集合，基于 **"铁三角"架构**：`ai-agent-sdk → ai-tool-sdk → ai-client-sdk`。

## 模块一览

| 模块 | 定位 | 说明 |
|---|---|---|
| `ai-client-sdk` | 通信基座 | HTTP/SSE 传输、Tool Calling 协议、Key 轮询、重试熔断 |
| `ai-tool-sdk` | 工具基座 | AgentTool 契约、@Tool/@ToolParam 注解、Schema 生成、安全沙箱 |
| `ai-agent-sdk` | 编排引擎 | ReAct 循环、虚拟线程并发调度、Token 裁剪、生命周期事件 |

## 技术亮点

- **零第三方 HTTP 依赖** — JDK 21 原生 `HttpClient` + 虚拟线程，Jar 极轻
- **零 AI 框架** — 不引入 Spring AI / LangChain4j，直接基于 OpenAI 兼容协议手写 DTO
- **框架无关** — 纯 Java 可用，Spring Boot 自动装配可选
- **高可用内置** — API Key 轮询、故障隔离、指数退避重试、JSON 容错开箱即用
- **安全沙箱** — 工具执行链式拦截：参数校验 → 危险命令过滤 → 超时控制

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

// SSE 流式
client.streamText(request, new StreamListener() {
    @Override public void onChunk(String chunk) { /* 推送给前端 */ }
    @Override public void onComplete() { /* 结束 */ }
});

// 结构化输出
MyResult result = client.generateObject(request, MyResult.class);
```

### 2. ai-tool-sdk — 定义工具

```xml
<dependency>
    <groupId>com.realapex</groupId>
    <artifactId>ai-tool-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

**方式一：实现 AgentTool 接口**

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
```

**方式二：@Tool 注解（Spring 环境自动扫描）**

```java
@Component
public class MyTools {
    @Tool(name = "get_weather", description = "查询城市天气")
    public String getWeather(@ToolParam(description = "城市名") String city) {
        return weatherService.query(city);
    }
}
```

### 3. ai-agent-sdk — 智能体编排

```xml
<dependency>
    <groupId>com.realapex</groupId>
    <artifactId>ai-agent-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```java
// 创建 AgentRunner（单例，线程安全）
AgentRunner agentRunner = new AgentRunner(aiClient, new SchemaGenerator());

// 注册工具
ToolRegistry registry = new ToolRegistry(new SchemaGenerator());
registry.register(new CalculatorTool());

// 运行 Agent
AgentResult result = agentRunner.run(AgentRequest.builder()
        .systemPrompt("你是一个数学助手，遇到计算时使用 calculator 工具")
        .userPrompt("计算 (123 + 456) × 789 的结果")
        .tools(registry.getAll())
        .maxSteps(5)
        .build());

System.out.println(result.getFinalText());
System.out.println("Token 消耗: " + result.getTotalTokens());
System.out.println("执行步数: " + result.getTotalSteps());
```

### 4. Spring Boot 自动装配

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
    @Autowired private AiClient aiClient;
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

## 配置参考

### ai-client-sdk (`ai.sdk.*`)

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `base-url` | `https://api.deepseek.com/v1` | 兼容 OpenAI 的 API 端点 |
| `api-keys` | —（必填） | API Key 列表，Round-Robin 轮询 |
| `model` | `deepseek-chat` | 默认模型 |
| `timeout` | `60s` | 请求超时 |
| `read-timeout` | `30s` | SSE 流式无数据包最大等待间隔 |
| `max-retries` | `3` | 429/5xx 重试次数 |
| `key-blacklist-duration` | `10m` | Key 故障隔离时长（401/402） |

### ai-agent-sdk (`ai.agent.*`)

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `max-steps` | `10` | ReAct 循环最大步数 |
| `max-context-tokens` | `8000` | 触发上下文裁剪的 Token 阈值 |
| `model` | — | 覆盖 ai-client-sdk 的默认模型 |

---

## 项目结构

```
ai-workbench/
 ├── README.md              <-- 唯一文档（本文件）
 ├── pom.xml                <-- 父 POM
 ├── ai-client-sdk/         <-- 通信基座
 ├── ai-tool-sdk/           <-- 工具基座
 └── ai-agent-sdk/          <-- 编排引擎
```

> API 详细文档见各模块源码 **Javadoc**。发布 Jar 附带 `-sources.jar`，IDE 自动读取 Javadoc 提供参数提示与异常处理建议。
