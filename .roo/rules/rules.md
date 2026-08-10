# AI Workbench SDK 工程规范 (Java 21 + Maven 多模块)

## 1. 铁三角架构

```
ai-workbench/
 ├── ai-client-sdk/    --> 通信基座：HTTP/SSE、Tool Calling、重试熔断、Key轮询
 ├── ai-tool-sdk/      --> 工具基座：AgentTool契约、@Tool/@ToolParam、Schema生成、安全沙箱
 └── ai-agent-sdk/     --> 编排引擎：ReAct循环、虚拟线程并发、上下文裁剪、生命周期事件
```

**单向依赖**：`ai-agent-sdk → ai-tool-sdk → ai-client-sdk`，绝不反向。

**4 条铁律**：
1. `ai-tool-sdk` 禁止引入 JDBC、K8s Client、Redis Client 等业务/数据源依赖
2. 场景 SDK 只组合工具，不实现 `@Tool`（工具实现在领域扩展包中）
3. 危险工具必须接入 `ai-tool-sdk` 安全拦截器链
4. 单向依赖链路，禁止反向或循环引用

---

## 2. 工程化规范

- **Maven**：父 POM 统一 `<dependencyManagement>`，子模块只声明自己需要的依赖
- **JDK 21**：虚拟线程、Record、Sealed Interface
- **Lombok**：`@Data`、`@Slf4j`、`@Builder`
- **Jackson**：JSON 序列化/反序列化
- **HTTP**：JDK 原生 `java.net.http.HttpClient`，禁止 OkHttp
- **AI 框架**：禁止 Spring AI、LangChain4j，直接手写 OpenAI 兼容协议的 JSON DTO
- **Spring 集成**：`spring-boot-autoconfigure` 设为 `<optional>true</optional>`

---

## 3. 文档规范

- 工程只保留根目录 `README.md`（简介 + Maven坐标 + 3-5行快速示例）
- 禁止独立 ARCHITECTURE.md / API-SPEC.md / DESIGN.md
- API 规范 = Javadoc：所有 public 方法必须写 `@param`、`@return`、`@throws`

---

## 4. 包结构与命名

| 模块 | 包名 | 子包 |
|------|------|------|
| ai-client-sdk | `com.realapex.client` | client/impl、model、stream、config、executor、exception、autoconfigure |
| ai-tool-sdk | `com.realapex.tool` | contract、annotation、schema、security |
| ai-agent-sdk | `com.realapex.agent` | execution、tool、context、event、config、exception |

---

## 5. 核心接口

```java
// ai-client-sdk
public interface AiClient {
    String generateText(AiRequest request);
    AiResponse generate(AiRequest request);           // 含 tool_calls + usage
    void streamText(AiRequest request, StreamListener listener);
    <T> T generateObject(AiRequest request, Class<T> type);
}

// ai-tool-sdk
public interface AgentTool<REQ, RESP> {
    String name();
    String description();
    Class<REQ> requestClass();
    RESP execute(REQ request);
    default ToolResult executeSafely(REQ req);
}

// ai-agent-sdk
public class AgentRunner {
    AgentResult run(AgentRequest request);
    <T> AgentResult run(AgentRequest request, Class<T> outputClass);
}
```

---

## 6. 关键设计约束

- **API Key**：多 Key Round-Robin 轮询，401 隔离 10min，429/5xx 指数退避 + 随机抖动
- **Tool Calling**：OpenAI Function Calling 格式，SSE 增量拼接 ToolCall，Message 角色完整（system/user/assistant(tool_calls)/tool(tool_call_id)）
- **Schema 生成**：Jackson 反射 Record/POJO → JSON Schema，读取 `@ToolParam` 描述和必填标记
- **安全沙箱**：`ToolSecurityInterceptor` 链（ParamValidator → DangerousCommandFilter → TimeoutInterceptor），11 种危险模式正则检测
- **ReAct 循环**：while 循环 → LLM 调用 → tool_calls 解析 → 虚拟线程并行执行 → 结果回传 → 上下文裁剪
- **错误自愈**：工具异常捕获堆栈包装为 toolResult 回传 LLM 驱动自动修正
- **上下文裁剪**：滑动窗口 + Token 估算，tool 消息必须与 assistant(tool_calls) 配对裁剪，system + 最新 user 永不裁剪

---

## 7. AI 执行指令

1. 不生成独立设计文档，核心信息写在 Javadoc 和 README
2. 遵守 `Agent → Tool → Client` 单向依赖，禁止反向引用
3. 先写 public API 接口 + Javadoc，再写实现
4. 网络 I/O 必须有超时、重试、降级
5. 关键路径打 SLF4J 日志
6. 涉及外部数据源/系统命令的工具必须接入安全拦截器
7. 当前聚焦铁三角 3 模块，上层场景和领域工具包后续迭代
