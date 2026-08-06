package com.realapex.client.client;

import com.realapex.client.exception.AiClientException;
import com.realapex.client.model.AiRequest;
import com.realapex.client.skill.BaseSkill;

/**
 * AI 大模型通信客户端——顶层通用调用接口。
 * <p>屏蔽不同厂商 API 差异、Key 轮询、重试、SSE 流拼装、JSON 容错等底层复杂性，
 * 只向业务层暴露最干净的文本/对象。</p>
 *
 * <h3>快速上手</h3>
 * <pre>{@code
 * AiConfig config = AiConfig.builder()
 *         .apiKeys(List.of("sk-xxx"))
 *         .build();
 * AiClient client = DefaultAiClient.create(config);
 * String reply = client.generateText(
 *         AiRequest.builder()
 *                 .messages(List.of(Message.user("你好")))
 *                 .build());
 * }</pre>
 */
public interface AiClient {

    /**
     * 同步生成文本。
     * <p>适合知识库生成、后台批处理等不需要实时反馈的场景。</p>
     *
     * @param request 统一请求对象（包含 model、messages、temperature 等），不能为空
     * @return 大模型返回的完整文本
     * @throws AiClientException 网络超时、API Key 失效、响应解析失败时抛出
     */
    String generateText(AiRequest request);

    /**
     * SSE 流式输出。
     * <p>适合前端实时对话、慢 SQL 分析过程展示等需要逐字输出的场景。
     * 通过 {@link StreamListener} 回调解耦，不绑定任何特定 Web 框架。</p>
     *
     * @param request  统一请求对象，不能为空
     * @param listener 流式回调监听器，不能为空
     * @throws AiClientException 网络超时、API Key 失效时抛出
     */
    void streamText(AiRequest request, StreamListener listener);

    /**
     * 结构化 JSON 生成。
     * <p>适合文档校验、错别字抽取、结构化提取等需要大模型输出严格 JSON 的场景。
     * 内部自动注入 {@code response_format: { "type": "json_object" }}，
     * 并容错清洗 Markdown 代码块标记后反序列化为目标 Java 类型。</p>
     *
     * @param request      统一请求对象，不能为空
     * @param responseType 目标 Java 类型，不能为空
     * @param <T>          目标类型
     * @return 反序列化后的 Java 对象
     * @throws AiClientException 网络超时、API Key 失效、JSON 解析失败时抛出
     */
    <T> T generateObject(AiRequest request, Class<T> responseType);

    /**
     * 驱动 Skill 执行入口。
     *
     * @param skill 技能实例，不能为空
     * @param input 技能输入
     * @param <I>   输入类型
     * @param <O>   输出类型
     * @return 技能执行结果
     * @throws AiClientException 执行过程中发生错误时抛出
     */
    <I, O> O executeSkill(BaseSkill<I, O> skill, I input);
}
