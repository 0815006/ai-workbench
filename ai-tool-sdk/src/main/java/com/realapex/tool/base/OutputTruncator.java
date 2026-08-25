package com.realapex.tool.base;

/**
 * 输出截断器——Token 爆炸保护。
 * <p>当 {@code readFile} 读到 50MB 日志、或 {@code execCommand} 输出 10 万行时，
 * 直接返回给 LLM 会瞬间爆满 Context Window。所有 {@code base} 工具返回结果前
 * 必须经过 {@link #truncate} 截断，超出部分替换为截断提示。</p>
 *
 * <h3>截断示例</h3>
 * <pre>{@code
 * 原始内容（5000 行，超出 20,000 字符）
 * → 前 20,000 字符 + "\n[...Content truncated, Total lines: 5000...]"
 * }</pre>
 */
public final class OutputTruncator {

    private OutputTruncator() {
    }

    /**
     * 按最大字符数截断文本，并附加总行数提示。
     *
     * @param content  原始内容（可为 null）
     * @param maxChars 最大字符数上限
     * @return 截断后的文本；未超限时原样返回
     */
    public static String truncate(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxChars) {
            return content;
        }

        int totalLines = countLines(content);
        String head = content.substring(0, maxChars);
        return head + "\n[...Content truncated, Total lines: " + totalLines + "...]";
    }

    /**
     * 统计文本总行数。
     *
     * @param content 文本内容
     * @return 行数（空文本为 0）
     */
    public static int countLines(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }
}