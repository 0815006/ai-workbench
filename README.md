# AI Workbench SDK

包含 3 个轻量级、低耦合的 AI 辅助开发 SDK，可按需独立引入。

## 模块一览

| 模块 | 说明 | 状态 |
|---|---|---|
| `ai-client-sdk` | 大模型通信、SSE 流式响应、结构化 JSON 解析 | ✅ 可用 |
| `ai-document-parser-sdk` | 多格式文档（Word/PDF/Excel）→ 标准 Markdown | 📋 规划中 |
| `ai-template-engine-sdk` | Markdown/JSON → Word/PDF/Excel 模板导出 | 📋 规划中 |

## 技术亮点

- **零第三方 HTTP 依赖** — 基于 JDK 21 原生 `HttpClient` + 虚拟线程，Jar 包极轻
- **零 AI 框架依赖** — 不引入 Spring AI / LangChain4j，直接基于 OpenAI 兼容协议
- **框架无关** — 通过 `StreamListener` 回调解耦，兼容 Spring MVC、WebFlux、纯 Java 等任意场景
- **高可用内置** — API Key 轮询、故障隔离（黑名单）、指数退避重试、JSON 容错解析开箱即用

---

## 快速开始 (QuickStart)

### 环境要求

- JDK 21+
- Maven 3.6+

### 1. 引入依赖

```xml
<dependency>
    <groupId>com.realapex</groupId>
    <artifactId>ai-client-sdk</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 纯 Java 项目（无需 Spring）

```java
import com.realapex.client.client.AiClient;
import com.realapex.client.client.impl.DefaultAiClient;
import com.realapex.client.config.AiConfig;
import com.realapex.client.model.AiRequest;
import com.realapex.client.model.Message;

// 创建客户端（全局单例，线程安全）
AiConfig config = AiConfig.builder()
        .apiKeys(List.of("sk-xxxxxxxxx"))
        .model("deepseek-chat")
        .build();
AiClient client = DefaultAiClient.create(config);

// 同步生成文本
String reply = client.generateText(AiRequest.builder()
        .messages(List.of(Message.user("你好，请介绍一下你自己")))
        .temperature(0.7)
        .build());
System.out.println(reply);
```

### 3. Spring Boot 项目（自动装配）

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
```

```java
@RestController
public class ChatController {

    @Autowired
    private AiClient aiClient;  // 自动注入

    @GetMapping("/chat")
    public String chat(@RequestParam String prompt) {
        return aiClient.generateText(AiRequest.builder()
                .messages(List.of(Message.user(prompt)))
                .build());
    }
}
```

### 4. SSE 流式输出（打字机效果）

```java
import com.realapex.client.client.StreamListener;
import com.realapex.client.model.AiRequest;
import com.realapex.client.model.Message;

@GetMapping("/chat/stream")
public SseEmitter stream(@RequestParam String prompt) {
    SseEmitter emitter = new SseEmitter(60000L);
    aiClient.streamText(
            AiRequest.builder()
                    .messages(List.of(Message.user(prompt)))
                    .build(),
            new StreamListener() {
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

### 5. 结构化 JSON 输出（文档校验等场景）

```java
// 定义目标 DTO
public record CheckResult(List<String> errors, int score) {}

// 大模型返回自动反序列化为 Java 对象
CheckResult result = aiClient.generateObject(
        AiRequest.builder()
                .messages(List.of(
                    Message.system("你是资深校对员，返回 JSON 格式：{\"errors\": [...], \"score\": 0-100}"),
                    Message.user("请检查：" + documentContent)))
                .build(),
        CheckResult.class);
```

---

## 配置参考

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `base-url` | `https://api.deepseek.com/v1` | 兼容 OpenAI 格式的 API 端点 |
| `api-keys` | —（必填） | API Key 列表，支持多 Key 轮询 |
| `model` | `deepseek-chat` | 默认模型名称 |
| `timeout` | `60s` | 请求超时时间 |
| `max-retries` | `3` | 遇 429/5xx 自动重试次数 |
| `retry-base-delay` | `1s` | 重试退避基础延迟 |
| `key-blacklist-duration` | `10m` | Key 故障隔离时长（401/402 后） |

---

## 项目结构

```
ai-workbench/
 ├── README.md              <-- 唯一文档（本文件）
 ├── pom.xml                <-- 父 POM（依赖版本管理）
 ├── ai-client-sdk/         <-- [已实现] 大模型通信 SDK
 ├── ai-document-parser-sdk <-- [规划中] 文档解析 SDK
 └── ai-template-engine-sdk <-- [规划中] 模板渲染 SDK
```

> API 详细文档见各模块源码中的 **Javadoc**。发布 Jar 包附带 `-sources.jar`，
> IDE 自动读取 Javadoc 提供参数提示与异常处理建议。
