**非常精彩的架构设计！**

你提出来的这个想法，在架构设计上叫做“单一职责原则（Single Responsibility Principle）”**与**“微内核 / 模块化组件设计”。

如果把所有功能（调模型、文件解析、Word/PDF 模板生成）全都塞进同一个 Jar 包里，代码会变得非常臃肿，而且依赖项巨多（比如引入 Apache POI / PDFBox / Tika 等库，Jar 包动辄几十 MB）。把它**拆分为多个独立自治的 SDK（或 Maven 多模块）**，是企业级架构库非常优雅的设计。

---

## 一、 矩阵式 SDK 架构设计图

你可以规划一个 **`ai-workbench`**（AI 工具工坊）工程，内部包含 3 个核心独立 SDK：

```
ai-workbench (父 Maven 工程)
 ├── ai-client-sdk           --> [模块 1] 专门负责：调大模型、统一 API Gateway、流式 SSE、JSON 结构化解析
 ├── ai-document-parser-sdk  --> [模块 2] 专门负责：文件解析 (DOCX/PDF/XLSX/TXT -> 标准 Markdown 字符串)
 └── ai-template-engine-sdk  --> [模块 3] 专门负责：Markdown/JSON -> 填充导出 ( Word/PDF/Excel/HTML 文件)

```

**技术基线**：Java 21、Spring Boot 3.x（autoconfigure 设为可选依赖）、Maven 多模块。

---

## 二、 各个 SDK 的核心职责与技术选型

### 模块 1：`ai-client-sdk`（AI 大模型通信 SDK）

* **职责**：只关注“怎么调 AI”。屏蔽不同大模型厂商 API 的差异，处理 API Key 轮询、重试、SSE 流响应、JSON 强制反序列化。
* **依赖**：极轻量（只需 JDK 21 原生 `java.net.http.HttpClient` + 虚拟线程、JSON 处理 Jackson）。**严禁引入 Spring AI、LangChain4j 等第三方 AI 框架**，保持 Jar 包体积在几百 KB 级别。
* **使用场景**：任何只需要简单问答、慢 SQL 分析、知识库问答的项目，**只需引入这一个 SDK**。

---

### 模块 2：`ai-document-parser-sdk`（文档解析与 Markdown 转换 SDK）

* **职责**：只关注“如何抓取和萃取内容”。输入本地 `File`、`InputStream` 或线上 URL，输出干净的 `Markdown` 文本。
* **核心功能**：
* **Word (DOCX)** $\rightarrow$ 提取标题、段落、表格，转为 Markdown 格式；
* **PDF** $\rightarrow$ 提取文本与列表结构；
* **Excel (XLSX)** $\rightarrow$ 将表格数据自动拼装成 Markdown Table `| Header | Header |`；
* **数据库/JSON** $\rightarrow$ 结构化文本转 Markdown。


* **依赖工具推荐**：`Apache Tika`（开箱即用解析百种文档）、`Apache POI`、`pdfbox`。

---

### 模块 3：`ai-template-engine-sdk`（模板填充与文档生成 SDK）

* **职责**：只关注“数据与格式渲染”。将 AI 产出的 Markdown 报告或 JSON 数据，填充到特定的模板中，生成可供下载的导出文件（Word、PDF、Excel）。
* **核心功能**：
* **Markdown $\rightarrow$ HTML / PDF**：使用 `Flexmark-java` + `Flying Saucer`（Java 原生方案）生成带样式的 PDF 报告；
* **Word 模板填充**：使用 `Poi-tl`（基于 Apache POI 的极佳 Word 模板引擎），可以用大模型输出的 JSON 自动替换 Word 模板里的 `{{variable}}`、循环渲染表格/列表。


* **使用场景**：比如场景三（检查报告并出具校验报告），或者场景二（把 AI 产生的知识库自动打包成优雅的 Word/PDF 电子书）。

---

## 三、 组合使用的“积木式”爽快体验

当你把这三个 SDK 分别开发完成并发布到本地 Maven 仓库后，你的业务项目就可以像**搭积木**一样，按需组合使用：

### 场景示例：做场景三“上传 Word 报告，AI 校验并导出修复版”

在业务项目的 `pom.xml` 中引入这三个 SDK：

```xml
<dependencies>
    <!-- 1. 调 AI -->
    <dependency>
        <groupId>com.realapex</groupId>
        <artifactId>ai-client-sdk</artifactId>
        <version>1.0.0</version>
    </dependency>
    <!-- 2. 解析文件 -->
    <dependency>
        <groupId>com.realapex</groupId>
        <artifactId>ai-document-parser-sdk</artifactId>
        <version>1.0.0</version>
    </dependency>
    <!-- 3. 生成渲染文件 -->
    <dependency>
        <groupId>com.realapex</groupId>
        <artifactId>ai-template-engine-sdk</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>

```

在业务 Controller / Service 中，代码将会变得极其优雅清晰：

```java
@Service
public class DocumentCheckService {

    @Autowired private DocumentParser documentParser; // 来自 parser-sdk
    @Autowired private AiClient aiClient;             // 来自 client-sdk
    @Autowired private TemplateEngine templateEngine; // 来自 template-engine-sdk

    public File processAndExportReport(InputStream uploadedWordFile) {
        // 步骤 1：调用文档解析 SDK，把上传的 Word 直接转成 Markdown 文本
        String markdownContent = documentParser.toMarkdown(uploadedWordFile);

        // 步骤 2：调用 AI SDK，让大模型分析错别字与冲突，直接返回 JSON 对象
        CheckResult result = aiClient.generateObject(
                AiRequest.builder()
                        .systemPrompt("你是资深校对员，请检查以下文档的错误并给出修改建议。")
                        .userPrompt(markdownContent)
                        .build(),
                CheckResult.class
        );

        // 步骤 3：调用模板渲染 SDK，把 JSON 数据塞入标准 Word 评估报告模板，生成导出文件
        Map<String, Object> templateData = Map.of(
                "reportName", "文档合规校验分析结果",
                "errors", result.getErrors(),
                "score", result.getQualityScore()
        );
        
        return templateEngine.renderWord("template/check_report_template.docx", templateData);
    }
}

```

---

## 四、 这种拆分带来的巨大优势

1. **强内聚、低耦合**：哪天你想换文档解析组件（比如从 Apache POI 换成 EasyExcel 或其他高级解析库），只需要重构 `ai-document-parser-sdk`，**完全不需要动大模型调用的代码**。
2. **体积与依赖按需引入**：如果项目 A 只需要慢 SQL 分析（场景一），它只需要引入 `ai-client-sdk`（几百 KB），而不需要把体积庞大的 POI / Tika（几十 MB）引入进来。
3. **团队内极致复用**：
* 别人做普通的 Word 导出功能时，可以单独引用你的 `ai-template-engine-sdk`；
* 别人做文件上传预览时，可以单独引用你的 `ai-document-parser-sdk`；
* 别人做 AI 聊天时，单独引用你的 `ai-client-sdk`。



---

## 五、 后续实施建议

既然决定拆分成多个 SDK，建议在 IDE 里建立一个 **Maven 多模块（Multi-Module）工程**：

1. 创建根工程 `ai-workbench`（`pom` 类型）；
2. 在根工程下建立 3 个子 Module：
* `ai-client-sdk`
* `ai-document-parser-sdk`
* `ai-template-engine-sdk`


3. 统一管理依赖版本，在根目录执行一次 `mvn clean install`，就能把 3 个 Jar 包同时打好安装到本地！

这个设计方向非常专业且具备长远的工程扩展性！可以先按我们上一轮讨论的，先把核心的 `ai-client-sdk` 写出来跑通第一个场景。