package com.realapex.client.skill;

import com.realapex.client.client.AiClient;
import com.realapex.client.exception.AiClientException;

/**
 * Skill 泛型基类——确定性单步技能抽象。
 * <p>Skill 代表一个明确的、可复用的 AI 能力单元（如文本摘要、代码审查、翻译）。
 * 业务方继承此类，实现 {@link #execute(AiClient, Object)} 定义具体逻辑。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class TextSummarySkill extends BaseSkill<String, String> {
 *     @Override
 *     public String execute(AiClient client, String input) {
 *         return client.generateText(AiRequest.builder()
 *                 .messages(List.of(
 *                     Message.system("你是一个专业的文本摘要助手。"),
 *                     Message.user("请总结以下内容：" + input)))
 *                 .build());
 *     }
 * }
 * }</pre>
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 */
public abstract class BaseSkill<I, O> {

    /**
     * 执行技能。
     *
     * @param client AiClient 实例，由 SDK 注入
     * @param input  技能输入
     * @return 技能输出
     * @throws AiClientException 执行过程中发生错误时抛出
     */
    public abstract O execute(AiClient client, I input);
}
