package com.realapex.client.agent;

/**
 * [二期] Agent 工具定义接口。
 * <p>代表一个可供 Agent 调用的外部工具（如数据库查询、API 调用）。
 * 与 OpenAI Function Calling 规范对齐。</p>
 */
public interface Tool {

    /** 工具名称（对应 function name） */
    String name();

    /** 工具描述（供大模型理解工具用途） */
    String description();

    /** 参数 JSON Schema */
    String parametersSchema();

    /** 执行工具，返回 JSON 字符串结果 */
    String execute(String argumentsJson);
}
