核心选型总结
层次	选型	理由
AI 协议	OpenAI Chat Completions 兼容格式	行业事实标准，跨厂商通用
后端 HTTP 客户端	java.net.http.HttpClient（JDK 内置）	零额外依赖，ofLines() 天然适配 SSE 按行消费
后端流式输出	Spring MVC SseEmitter（SSE）	Servlet 原生支持，无需 WebFlux
并发模型	JDK 21 Virtual Threads	长连接低成本阻塞，简化异步编码
前端流式消费	fetch + ReadableStream + 手动 SSE 解析	支持 POST + 自定义头，EventSource 无法满足
AI 框架	零 AI SDK	无 Spring AI / LangChain4j，完全自主可控
Agent 工具	硬编码 6 大内置工具（OpenAI Function Calling Schema）	零配置、明确安全边界
Markdown 渲染	marked（GFM）	轻量
流式输出实现链路
前端 POST /api/chat/send，带 JSON 请求体 + Accept: text/event-stream
后端 ChatController 返回 SseEmitter，在虚拟线程中：
构造 OpenAI 兼容请求体 { model, messages, stream: true }
HttpClient.send() + BodyHandlers.ofLines() → 逐行消费 LLM 的 SSE 响应
解析 data: 行中的 choices[0].delta.content，通过 SseEmitter.event().name("message").data(chunk) 推给前端
前端 response.body.getReader() 逐块读取，手动解析 SSE event:/data: 行，实时追加到消息气泡中渲染
文档覆盖内容
整体架构图、后端/前端技术栈、HTTP 客户端对比选型
SSE 流式输出完整实现（含非流式降级、超时保护、中断机制）
Agent 智能体的 While 循环 + Tool Calling + 增量拼接实现
6 大内置工具的 Schema 与安全沙箱设计
前端 ReadableStream 手动 SSE 解析 vs EventSource 对比
数据库表结构、通信协议、安全设计
待完善方向（外部插件、断线重连、Token 计费等）