针对 **`ai-client-sdk`** 这个通信底座，我们要把它做成一个“掉进去就能用、绝不拖泥带水”的纯技术 SDK。

以下是关于这个模块从**架构层级、核心包结构、关键技术选型到接口抽象设计**的完整落地分析。

---

## 一、 核心架构与包结构规划

在包结构上，我们要严格做到**高内聚、低耦合**。整体结构建议如下：

```
ai-client-sdk
 ├── annotation/                  --> 配合结构化输出的注解（如 @AiSchema）
 ├── autoconfigure/               --> Spring Boot 自动配置类、配置属性定义
 ├── client/                      --> 核心客户端接口及默认实现
 │    ├── AiClient.java           (顶层通用调用接口)
 │    └── impl/
 │         └── DefaultAiClient.java
 ├── config/                      --> SDK 全局配置模型 (Key 列表、超时、重试策略)
 ├── exception/                   --> 统一异常体系 (AiApiException, RateLimitException 等)
 ├── executor/                    --> 底层网络通信与重试/轮询调度执行器
 │    ├── KeySelector.java        (API Key 轮询与健康检查)
 │    └── RetryHandler.java        (HTTP 429/5xx 指数退避重试处理器)
 ├── model/                       --> 统一 DTO 模型 (Message, Request, Usage 等)
 └── skill/                       --> 业务 Skill 驱动框架抽象
      ├── BaseSkill.java          (Skill 泛型基类)
      └── SkillContext.java       (Skill 执行上下文)

```

---

## 二、 关键技术选型与依赖控制

为了确保 SDK **极轻量**，技术基线对齐 Java 21 + Spring Boot 3.x，依赖必须克制：

```xml
<dependencies>
    <!-- 1. HTTP 通信：完全使用 JDK 21 原生 java.net.http.HttpClient，零第三方依赖 -->
    <!--    配合虚拟线程（Virtual Threads）实现最高并发性能与最低资源占用 -->
    <!--    无需引入 OkHttp、okhttp-sse 或任何第三方 HTTP 框架 -->
    <!--    严禁引入 Spring AI、LangChain4j 等第三方 AI 框架——迭代快、冗余抽象多、Jar 体积暴增 -->

    <!-- 2. JSON 处理：Jackson（Spring 生态默认），避免 Fastjson 的版本兼容隐患 -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>

    <!-- 3. Spring Boot 条件配置（选配，用于自动注入，设为 optional） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-autoconfigure</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>

```

---

## 三、 4 大核心职责的落地实现逻辑

### 1. 屏蔽不同大模型厂商 API 的差异

* **统一底层协议**：目前 DeepSeek、通义千问、智谱清言、Moonshot 以及各类中转 API，全面兼容 OpenAI 的 HTTP REST 规范。SDK 内部将输入参数统一映射为 OpenAI 格式的 JSON 体：

$$\text{Payload} = \{\text{"model"}: \dots, \text{"messages"}: [\dots], \text{"temperature"}: \dots\}$$


* **适配器拓展机制**：针对极少数不兼容 OpenAI 格式的模型，提供 `RequestAdapter` 适配器接口，便于未来扩展。

### 2. API Key 轮询与故障重试（高可用保障）

这是后台批量任务（如场景二知识库生成）稳定运行的关键：

* **Key 轮询与健康度管理 (`KeySelector`)**：
支持配置多个 Key：`api-keys: [sk-1, sk-2, sk-3]`。默认采用 Round-Robin 轮询算法。若某个 Key 抛出 `401 (Unauthorized)` 或 `402 (Insufficient Balance)`，自动将该 Key 移入“黑名单”，临时隔离 10 分钟。
* **指数退避重试 (Exponential Backoff)**：
基于 JDK 21 虚拟线程 + `CompletableFuture`，当捕获到 `429 (Rate Limit)` 或 `5xx (Server Error)` 时，自动按 $1\text{s} \to 2\text{s} \to 4\text{s}$ 的退避延迟重试（可配置最大重试次数，默认 3 次）。虚拟线程极轻量，百级并发重试也不会阻塞平台线程。

### 3. SSE 流式响应（前端流畅输出）

支持与 Spring MVC 的 `SseEmitter` 无缝衔接，解决阻塞感：

* 利用 JDK 21 `HttpClient` 的 `BodyHandlers.ofLines()` 逐行读取 SSE 流，解析 `data: {...}` 中的 `delta.content`。
* 在虚拟线程中运行阻塞式 SSE 读取，通过回调函数同步推送到 Spring 的 `SseEmitter`：

```java
// 业务层一行代码将 StreamListener 对接 SseEmitter
@GetMapping("/chat/stream")
public SseEmitter stream() {
    SseEmitter emitter = new SseEmitter(60000L);
    aiClient.streamText(request, new StreamListener() {
        @Override
        public void onChunk(String chunk) {
            try { emitter.send(SseEmitter.event().data(chunk)); } catch (Exception e) {}
        }
        @Override
        public void onComplete() { emitter.complete(); }
        @Override
        public void onError(Throwable e) { emitter.completeWithError(e); }
    });
    return emitter;
}
```

### 4. JSON 强制结构化与反序列化（对标 `generateObject`）

场景三（文档错别字检查）和场景一（慢 SQL 结构化优化）需要大模型输出**严格的 JSON**，而不是漫无边际的自然语言。

* **Prompt 级别约束**：请求体中自动注入 `response_format: { "type": "json_object" }`（若模型支持）。
* **容错解析引擎 (`JsonRepairParser`)**：大模型有时会返回带着 Markdown 标记的代码块，如：
```markdown
```json
{ "errors": [...] }

```


```
SDK 的解析工具类会自动截取匹配从第一个 `{` 到最后一个 `}` 的内容，剔除 Markdown 伪代码标记后，再强转为 Java DTO 对象；若解析失败，自动触发 1 次降级重试。


```



---

## 四、 核心 API 接口设计预览

我们在 SDK 里最终暴露给使用者的顶层接口 `AiClient`，设计得极其优雅简洁：

```java
public interface AiClient {

    /**
     * 1. 同步生成文本 (适合知识库生成、后台批处理)
     */
    String generateText(AiRequest request);

    /**
     * 2. SSE 流式输出 (适合前端实时对话、慢 SQL 分析过程展示)
     *    通过 StreamListener 回调解耦，不绑定任何特定 Web 框架
     */
    void streamText(AiRequest request, StreamListener listener);

    /**
     * 3. 结构化 JSON 生成 (适合文档校验、错别字抽取、结构化提取)
     */
    <T> T generateObject(AiRequest request, Class<T> responseType);

    /**
     * 4. 驱动 Skill 执行入口
     */
    <I, O> O executeSkill(BaseSkill<I, O> skill, I input);
}

/**
 * 流式响应监听器——SDK 与上层框架的解耦桥梁。
 * 业务方可在回调中自由对接 SseEmitter、WebSocket、日志等任意消费方式。
 */
public interface StreamListener {
    void onChunk(String chunk);
    void onComplete();
    void onError(Throwable throwable);
}
```

---

## 五、 在 Spring Boot 中 `application.yml` 的配置形态

业务工程引入这个 SDK 的 Jar 包后，只需在 `application.yml` 中做如下配置，就能自动生效：

```yaml
ai:
  sdk:
    base-url: https://api.deepseek.com/v1   # 兼容 OpenAI 格式的 endpoint
    api-keys:                               # 支持多 Key 轮询
      - sk-xxxxxxxxx1
      - sk-xxxxxxxxx2
    model: deepseek-chat                   # 默认模型
    timeout: 60s                            # 请求超时时间
    max-retries: 3                          # 遇 429 / 5xx 自动重试次数

```

---

## 六、 总结

`ai-client-sdk` 的设计核心就是：**“把底层复杂的 HTTP 连接、网络异常重试、Key 轮询控频、SSE 字符流拼装、JSON 杂质清洗全部吃掉，只向业务层暴露最干净的文本/对象”**。

这套 SDK 只要写完并运行 `mvn clean install` 打入本地仓库，接下来你就可以基于它非常轻松地把场景一（慢 SQL 分析）给优雅跑通了。