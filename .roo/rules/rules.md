# AI Workbench SDK 工程规范 (Java 21 + Maven 多模块)

## 1. 项目概述与目录结构

`ai-workbench` 是一个 **Maven 多模块（Multi-Module）** 工程，提供 3 个轻量级、低耦合的 AI 辅助开发 SDK，可按需独立引入。

```
ai-workbench/                        (父 POM 工程，packaging: pom)
 ├── README.md                       <-- 整个工程唯一的文档导航（QuickStart）
 ├── pom.xml
 ├── ai-client-sdk/                  --> [模块1] 大模型通信、SSE 流式、JSON 结构化解析
 ├── ai-document-parser-sdk/         --> [模块2] 文档解析（Word/PDF/Excel → Markdown）
 └── ai-template-engine-sdk/         --> [模块3] Markdown/JSON → Word/PDF/Excel 模板导出
```

**核心设计原则**：强内聚、低耦合、依赖极轻量。每个模块独立自治，业务方按需引入，互不传染。

---

## 2. 工程化规范

### 2.1 Maven 多模块管理
- **父 POM**：统一管理依赖版本号（`<dependencyManagement>`），不引入实际依赖。
- **子模块**：只声明自己需要的依赖，禁止在子模块间传递不必要的传递性依赖。
- **构建**：根目录执行 `mvn clean install` 一次性打包全部模块。
- **发布**：Jar 包附带 `-sources.jar`（包含 Javadoc），让调用方的 IDE 能读取接口文档。

### 2.2 依赖控制（核心原则：极轻量）
- **HTTP 通信**：JDK 21 原生 `java.net.http.HttpClient`，零第三方依赖，配合虚拟线程实现最高并发性能。**严禁**引入 OkHttp 或其他第三方 HTTP 框架。
- **AI 框架**：**严禁**引入 Spring AI、LangChain4j 等第三方 AI 框架——迭代快、冗余抽象多、Jar 体积暴增。SDK 直接基于 OpenAI Chat Completions 兼容协议手写 JSON DTO。
- **JSON 处理**：Jackson（`jackson-databind`），与 Spring 生态默认一致。
- **Spring 集成**：`spring-boot-autoconfigure` 设为 `<optional>true</optional>`，不强制依赖 Spring。
- **文档解析（parser-sdk）**：Apache Tika / POI / PDFBox（仅在该模块内声明，不向上传递）。
- **模板引擎（template-sdk）**：Poi-tl + Flying Saucer（仅在该模块内声明）。
- **禁止**引入体积臃肿的依赖，每个模块的 Jar 包应控制在合理体积内。

### 2.3 Java 版本与语法
- **Java 版本**：Java 21，可使用虚拟线程、Record 类等特性。
- **代码风格**：
  - 接口返回数据优先使用 Java **Record** 类（不可变 DTO）。
  - 使用 **Lombok**（`@Data`, `@Slf4j`, `@Builder`）减少样板代码。
  - 所有 public API 方法参数使用 `@NonNull` / `@Nullable` 注解明确空值语义。

---

## 3. 文档规范（最高优先级）

### 3.1 "1 + 1" 文档策略
- **唯一 `.md` 文件**：工程只保留一个 `README.md` 在根目录，包含：
  1. 工程一句话简介 + 3 个模块目录关系
  2. 每个 SDK 的 Maven 依赖坐标
  3. 每个 SDK 核心 API 的最简单调用示例（3-5 行代码）
- **API 规范 = Javadoc**：
  - **禁止**编写独立的 `ARCHITECTURE.md`、`API-SPEC.md`、`DESIGN.md` 等设计文档。
  - 所有接口、public 方法必须写完整 Javadoc（`@param`、`@return`、`@throws`、使用场景说明）。
  - AI 编码助手通过读取 `-sources.jar` 中的 Javadoc 获取 API 信息，不需 Markdown 表格。

### 3.2 Javadoc 示例（AI 友好格式）

```java
/**
 * 将输入的文件解析为 Markdown 格式字符串。
 * <p>支持 .docx、.pdf、.xlsx 等常见办公文档格式。</p>
 *
 * @param file 待解析的文件，不能为空
 * @return 解析后的标准 Markdown 字符串
 * @throws DocumentParseException 文件格式不支持或解析失败时抛出
 */
String toMarkdown(@NonNull File file) throws DocumentParseException;
```

**关键规范**：所有接口方法必须在 `@throws` 中显式声明 SDK 自定义运行时异常（即使是非受检异常），以便 AI 编码助手（Copilot / Cursor）在生成调用代码时自动提示业务方处理。例如：

```java
/**
 * 同步调用大模型生成文本。
 *
 * @param request 统一请求对象（包含 model、messages、temperature 等），不能为空
 * @return 大模型返回的完整文本
 * @throws AiClientException 网络超时、API Key 失效、响应解析失败时抛出
 * @throws RateLimitException 请求频率超限（429），内部已自动重试，重试耗尽后抛出
 */
String generateText(@NonNull AiRequest request) throws AiClientException;
```

---

## 4. 包结构与命名规范

### 4.1 通用包名约定
- **基础包名**：`com.realapex`（域名 realapex.com）
- **子模块包名**：
  - `ai-client-sdk` → `com.realapex.client`
  - `ai-document-parser-sdk` → `com.realapex.parser`
  - `ai-template-engine-sdk` → `com.realapex.engine`

### 4.2 模块内部包结构（以 ai-client-sdk 为例）
```
ai-client-sdk/src/main/java/com/realapex/client/
 ├── annotation/          --> 结构化输出相关注解
 ├── autoconfigure/       --> Spring Boot 自动配置类（可选）
 ├── client/              --> 核心接口（AiClient）及默认实现
 │    └── impl/
 ├── config/              --> SDK 配置模型（Keys、超时、重试策略）
 ├── exception/           --> 统一异常体系
 ├── executor/            --> 底层 HTTP 通信（JDK HttpClient）、重试处理器、Key 轮询
 ├── model/               --> 统一 DTO（Message、Request、Response、Usage）
 ├── skill/               --> 确定性 Single-step 技能抽象（如：BaseSkill）
 └── agent/               --> [二期] 自主 Multi-step 智能体抽象（如：AgentExecutor, Tool）
```

---

## 5. API 设计规范

### 5.1 接口设计原则
- **顶层接口极简**：每个模块对外暴露的核心接口不超过 5 个方法。
- **屏蔽底层复杂性**：网络重试、Key 轮询、SSE 流拼装、JSON 容错全部封装在 SDK 内部。
- **最少知识原则**：调用方只需知道输入和输出，不关心实现细节。

### 5.2 AiClient 核心接口参考
```java
public interface AiClient {
    /** 同步生成文本（适合后台批处理） */
    String generateText(AiRequest request);

    /** SSE 流式输出，通过 StreamListener 解耦，不绑定特定 Web 框架 */
    void streamText(AiRequest request, StreamListener listener);

    /** 结构化 JSON 生成（适合文档校验、结构化提取） */
    <T> T generateObject(AiRequest request, Class<T> responseType);
}

/** 流式回调——业务方自由对接 SseEmitter、WebSocket 等 */
public interface StreamListener {
    void onChunk(String chunk);
    void onComplete();
    void onError(Throwable throwable);
}
```

### 5.3 异常体系
- 每个模块定义自己的根异常（如 `AiClientException`、`ParserException`、`TemplateException`）。
- 细分异常类型：`RateLimitException`、`AuthenticationException`、`ParseException` 等。
- 异常信息必须包含可操作的错误描述，便于调用方排查。

---

## 6. 关键设计约束

### 6.1 API Key 管理（ai-client-sdk）
- 支持多 Key 配置，默认 Round-Robin 轮询。
- 401/402 错误自动将对应 Key 隔离（临时黑名单 10 分钟）。
- 429/5xx 错误自动指数退避重试（1s → 2s → 4s，默认最多 3 次）。

### 6.2 JSON 容错解析
- 自动适配 `response_format: { "type": "json_object" }`（模型支持时）。
- 容错处理：自动剥离 Markdown 代码块标记（` ```json ... ``` `），提取 `{...}` 内容后反序列化。
- 解析失败时自动触发 1 次降级重试。

### 6.3 SSE 流式响应
- 逐行解析 `data: {...}` 中的增量内容。
- 正确处理 `[DONE]` 结束标记。
- 支持通过回调/Consumer 模式与 Spring `SseEmitter` 无缝衔接。

---

## 7. 配置文件设计（Spring Boot 自动配置）

业务方引入 SDK 后，在 `application.yml` 中的配置形态：

```yaml
ai:
  sdk:
    base-url: https://api.deepseek.com/v1
    api-keys:
      - sk-xxxxxxxxx1
      - sk-xxxxxxxxx2
    model: deepseek-chat
    timeout: 60s
    max-retries: 3
```

- 所有配置项提供合理默认值。
- 配置类使用 `@ConfigurationProperties(prefix = "ai.sdk")`。

---

## 8. 开发优先级

当前阶段聚焦 **`ai-client-sdk`**（大模型通信底座），优先跑通第一个场景后再扩展其他模块。

1. **首批**：`ai-client-sdk` — 统一大模型调用、SSE 流式、JSON 结构化输出（含 `skill/` 单步技能抽象）
2. **二期（ai-client-sdk 扩展）**：`agent/` 包 — 自主 Multi-step 智能体（AgentExecutor、Tool、Function Calling 循环）
3. **次批**：`ai-document-parser-sdk` — 文档解析转 Markdown
4. **末批**：`ai-template-engine-sdk` — 模板填充与文档生成导出

---

## 9. AI 执行指令

1. **"去废话"原则**：不生成独立设计文档，核心信息写在 Javadoc 和 README 里。
2. **最小依赖**：新增依赖前评估其体积和传递性影响，能用标准库解决的不用第三方库。
3. **优先实现接口**：先写 public API 接口 + Javadoc，再写内部实现。
4. **容错与重试**：所有网络 I/O 操作必须有超时、重试、降级策略。
5. **可观测性**：关键路径（API 调用、Key 轮询、重试）必须打日志（SLF4J），日志级别可配置。
