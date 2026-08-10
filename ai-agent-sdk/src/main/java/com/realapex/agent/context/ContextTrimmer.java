package com.realapex.agent.context;

import com.realapex.client.model.Message;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 上下文 Token 动态裁剪器。
 * <p>在每轮 ReAct 循环前检查消息历史是否超出 Token 预算，
 * 按滑动窗口策略裁剪最旧的消息，同时保证协议完整性。</p>
 *
 * <h3>裁剪规则（按优先级）</h3>
 * <ol>
 *   <li><b>保护 system 消息</b>：永远不裁剪 system 角色的消息</li>
 *   <li><b>保护最新 user 消息</b>：最近一条 user 消息必须保留</li>
 *   <li><b>Tool 消息配对规则</b>：若裁剪一条 tool 角色消息，必须同步裁剪其对应的
 *       assistant(tool_calls) 消息，防止 OpenAI/智谱等厂商返回
 *       HTTP 400 "Invalid messages sequence"</li>
 *   <li><b>过量单条 toolResult 截断</b>：若单条 tool result 内容过长，
 *       对其内容做字符截断而非直接删除</li>
 * </ol>
 */
@Slf4j
public class ContextTrimmer {

    /** 默认每 Token 约等于的字符数（粗略估算，实际因模型和语言而异） */
    private static final double DEFAULT_CHARS_PER_TOKEN = 4.0;

    /** 默认最大 Token 预算 */
    private static final int DEFAULT_MAX_TOKENS = 8000;

    private final int maxTokens;
    private final double charsPerToken;

    /**
     * 使用默认配置创建裁剪器。
     */
    public ContextTrimmer() {
        this(DEFAULT_MAX_TOKENS, DEFAULT_CHARS_PER_TOKEN);
    }

    /**
     * @param maxTokens     最大 Token 预算
     * @param charsPerToken 每 Token 约等于的字符数（中文约1-2，英文约4）
     */
    public ContextTrimmer(int maxTokens, double charsPerToken) {
        this.maxTokens = maxTokens;
        this.charsPerToken = charsPerToken;
    }

    /**
     * 裁剪消息列表至 Token 预算内。
     *
     * @param messages 原始消息列表
     * @return 裁剪后的消息列表（新列表，不修改原列表）
     */
    public List<Message> trim(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return messages;
        }

        int totalChars = estimateTotalChars(messages);
        int maxChars = (int) (maxTokens * charsPerToken);

        if (totalChars <= maxChars) {
            return new ArrayList<>(messages); // 无需裁剪
        }

        log.debug("上下文裁剪触发: {} chars / {} 预算, {} 条消息",
                totalChars, maxChars, messages.size());

        // 识别受保护的消息索引
        Set<Integer> protectedIndices = findProtectedIndices(messages);

        // 从头部开始裁剪（保留尾部），同时遵守配对规则
        List<Message> trimmed = trimFromHead(messages, protectedIndices, totalChars, maxChars);

        log.debug("上下文裁剪完成: {} → {} 条消息", messages.size(), trimmed.size());
        return trimmed;
    }

    // ==================== 内部实现 ====================

    /**
     * 估算消息列表总字符数。
     */
    private int estimateTotalChars(List<Message> messages) {
        int total = 0;
        for (Message msg : messages) {
            if (msg.getContent() != null) {
                total += msg.getContent().length();
            }
            // 粗略估算 tool_calls 的开销
            if (msg.getToolCalls() != null) {
                for (var tc : msg.getToolCalls()) {
                    if (tc.getArguments() != null) {
                        total += tc.getArguments().length();
                    }
                }
            }
        }
        return total;
    }

    /**
     * 找出受保护的消息索引集合：
     * system 消息 + 最后一条 user 消息。
     */
    private Set<Integer> findProtectedIndices(List<Message> messages) {
        Set<Integer> protected_ = new HashSet<>();
        int lastUserIdx = -1;

        for (int i = 0; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if ("system".equals(msg.getRole())) {
                protected_.add(i);
            }
            if ("user".equals(msg.getRole())) {
                lastUserIdx = i;
            }
        }

        if (lastUserIdx >= 0) {
            protected_.add(lastUserIdx);
        }

        return protected_;
    }

    /**
     * 从头部开始裁剪消息。
     * <p>策略：保留尾部（最新）的消息。当需要移除旧消息时，
     * 检查 tool 配对规则，将 tool 消息连同其对应的 assistant(tool_calls) 一起移除。</p>
     */
    private List<Message> trimFromHead(List<Message> messages, Set<Integer> protectedIndices,
                                        int totalChars, int maxChars) {
        List<Message> result = new ArrayList<>(messages);
        int currentChars = totalChars;

        // 收集 tool 消息的 toolCallId
        // 用于确定哪些 assistant 消息需要和 tool 消息配对移除
        Set<String> toolCallIdsToRemove = new HashSet<>();

        int i = 0;
        while (i < result.size() && currentChars > maxChars) {
            // 跳过受保护的消息
            if (protectedIndices.contains(i)) {
                i++;
                continue;
            }

            Message msg = result.get(i);
            int msgChars = estimateMessageChars(msg);

            if ("tool".equals(msg.getRole())) {
                // 移除 tool 消息，标记其 tool_call_id
                String toolCallId = msg.getToolCallId();
                result.remove(i);
                currentChars -= msgChars;

                // 移除后反向查找：如果前置 assistant(tool_calls) 的
                // 所有 tool 消息都已被移除，同步移除该 assistant 消息
                if (toolCallId != null) {
                    toolCallIdsToRemove.add(toolCallId);
                    removeOrphanedAssistant(result, toolCallIdsToRemove, i);
                }
                // 不递增 i，因为列表缩短了
            } else if ("assistant".equals(msg.getRole()) && msg.getToolCalls() != null) {
                // 检查此 assistant 消息的所有 tool_call 是否都在待移除列表中
                boolean allRemoved = msg.getToolCalls().stream()
                        .allMatch(tc -> toolCallIdsToRemove.contains(tc.getId()));
                if (allRemoved && !protectedIndices.contains(i)) {
                    result.remove(i);
                    currentChars -= msgChars;
                    // 清理已移除的 toolCallId
                    msg.getToolCalls().forEach(tc -> toolCallIdsToRemove.remove(tc.getId()));
                } else {
                    i++;
                }
            } else {
                // 普通 user/assistant 消息，直接移除
                result.remove(i);
                currentChars -= msgChars;
                // 不递增 i
            }
        }

        // 如果仍然超出预算，对超长单条 tool result 做内容截断
        if (currentChars > maxChars) {
            result = truncateLongToolResults(result, maxChars);
        }

        return result;
    }

    /**
     * 移除 tool 消息后，反向查找配对的 assistant(tool_calls) 消息。
     * 若其所有 tool 结果都已不在列表中，同步移除该 assistant 消息，
     * 防止出现"assistant with tool_calls without tool results"的非法消息序列。
     *
     * @param result               当前消息列表
     * @param toolCallIdsToRemove  已被标记移除的 tool_call_id 集合
     * @param fromIndex            从哪个索引开始反向查找（即刚被移除的 tool 消息的原位置）
     */
    private void removeOrphanedAssistant(List<Message> result,
                                          Set<String> toolCallIdsToRemove,
                                          int fromIndex) {
        for (int j = fromIndex - 1; j >= 0; j--) {
            Message candidate = result.get(j);
            if ("assistant".equals(candidate.getRole()) && candidate.getToolCalls() != null) {
                boolean allToolsRemoved = candidate.getToolCalls().stream()
                        .allMatch(tc -> toolCallIdsToRemove.contains(tc.getId()));
                if (allToolsRemoved) {
                    result.remove(j);
                }
                return; // 只处理最近的前置 assistant（一个 tool 只属于一个 assistant）
            }
        }
    }

    /**
     * 截断超长的 tool result 内容。
     */
    private List<Message> truncateLongToolResults(List<Message> messages, int maxChars) {
        // 简单策略：对超过平均长度的 tool 消息内容做截断
        int toolCount = 0;
        for (Message msg : messages) {
            if ("tool".equals(msg.getRole())) {
                toolCount++;
            }
        }
        if (toolCount == 0) {
            return messages;
        }

        int avgPerTool = maxChars / Math.max(toolCount, 1);
        for (Message msg : messages) {
            if ("tool".equals(msg.getRole()) && msg.getContent() != null
                    && msg.getContent().length() > avgPerTool) {
                String truncated = msg.getContent().substring(0, Math.max(avgPerTool, 100))
                        + "\n... [内容已截断，原长度: " + msg.getContent().length() + " 字符]";
                msg.setContent(truncated);
                log.debug("截断 tool result: {} → {} chars",
                        msg.getContent().length(), truncated.length());
            }
        }
        return messages;
    }

    /**
     * 估算单条消息的字符数。
     */
    private int estimateMessageChars(Message msg) {
        int chars = 0;
        if (msg.getContent() != null) {
            chars += msg.getContent().length();
        }
        if (msg.getToolCalls() != null) {
            for (var tc : msg.getToolCalls()) {
                if (tc.getArguments() != null) {
                    chars += tc.getArguments().length();
                }
            }
        }
        return chars;
    }
}
