package com.realapex.client.stream;

import com.realapex.client.model.ToolCall;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 流式工具调用增量拼接器。
 * <p>由于 SSE 返回的 {@code delta.tool_calls} 中 {@code arguments} 是分散的 JSON 片段，
 * 此 Buffer 负责按 {@code callId} 分组累积，最终拼接出完整的 {@link ToolCall} 对象。</p>
 *
 * <h3>处理策略</h3>
 * <ul>
 *   <li>按 {@code callId} 独立累积 {@code name} 和 {@code arguments} 片段</li>
 *   <li>兼容多厂商差异：首帧可能为空或包含未转义字符，本 Buffer 仅做字符串拼接不做 JSON 校验</li>
 *   <li>通过 {@code finish_reason} 或流结束信号触发完成</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * <p>使用 {@link ConcurrentHashMap} 保证 SSE 回调线程安全。</p>
 */
@Slf4j
public class StreamToolCallBuffer {

    /**
     * 单个工具调用的累积状态。
     */
    private static class Accumulator {
        String name;
        final StringBuilder arguments = new StringBuilder();
        boolean nameReceived;
    }

    private final Map<String, Accumulator> accumulators = new ConcurrentHashMap<>();
    private final List<ToolCall> completedCalls = new ArrayList<>();

    /**
     * 接收一个工具调用增量事件。
     *
     * @param chunk 工具调用增量片段
     */
    public void accept(StreamEvent.ToolCallChunk chunk) {
        if (chunk.callId() == null) {
            log.debug("ToolCallChunk with null callId, skipping");
            return;
        }

        Accumulator acc = accumulators.computeIfAbsent(chunk.callId(), k -> new Accumulator());

        // 名称只在有值时更新（首帧带名称，后续帧 name 为 null）
        if (chunk.name() != null && !chunk.name().isEmpty() && !acc.nameReceived) {
            acc.name = chunk.name();
            acc.nameReceived = true;
        }

        // 累积参数片段
        if (chunk.argumentsDelta() != null && !chunk.argumentsDelta().isEmpty()) {
            acc.arguments.append(chunk.argumentsDelta());
        }
    }

    /**
     * 将所有已累积的工具调用刷新为完整对象。
     * <p>调用此方法后，内部累积器清空，已完成的调用可通过 {@link #getCompletedCalls()} 获取。</p>
     */
    public void flush() {
        for (Map.Entry<String, Accumulator> entry : accumulators.entrySet()) {
            String callId = entry.getKey();
            Accumulator acc = entry.getValue();
            if (acc.name != null && !acc.name.isEmpty()) {
                completedCalls.add(ToolCall.of(callId, acc.name, acc.arguments.toString()));
            } else {
                log.debug("Skipping incomplete tool call: callId={}, name={}", callId, acc.name);
            }
        }
        accumulators.clear();
    }

    /**
     * 是否有未完成的累积。
     *
     * @return true 如果还有未 flush 的累积数据
     */
    public boolean hasPending() {
        return !accumulators.isEmpty();
    }

    /**
     * 获取所有已完成的工具调用。
     *
     * @return 已完成的 ToolCall 列表（不可变）
     */
    public List<ToolCall> getCompletedCalls() {
        return Collections.unmodifiableList(completedCalls);
    }

    /**
     * 重置所有状态。
     */
    public void reset() {
        accumulators.clear();
        completedCalls.clear();
    }

    /**
     * 获取当前累积的工具调用数量（含未 flush 的）。
     *
     * @return 累积中的工具调用数
     */
    public int pendingCount() {
        return accumulators.size();
    }
}
