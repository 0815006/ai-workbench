-- =====================================================================
-- AI Workbench / ai-client-sdk 固化的通用大模型调用日志与 Trace 表
-- 表名：sys_llm_invoke_log
-- 适用数据库：PostgreSQL 13+
-- 说明：
--   1. 本脚本由 SDK 固化在 classpath:db/sdk-migration/ 下。
--   2. 应用侧若启用 Flyway，可在 spring.flyway.locations 中追加
--      classpath:db/sdk-migration 统一调度执行；
--   3. 未启用 Flyway 的边缘应用，可开启 ai.client.trace.auto-ddl=true，
--      SDK 兜底用原生 JDBC 幂等执行本脚本。
-- =====================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS sys_llm_invoke_log (
    -- 1. 主键与异步追踪标识
    log_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trace_id VARCHAR(64) NOT NULL,        -- 全局 Trace ID (跨线程/异步任务传递)
    parent_log_id UUID,                   -- 父 Log ID (Agent 拆解子任务/多轮 Prompt 树状追踪)

    -- 2. 业务上下文 (由应用层异步上下文注入)
    scene_type VARCHAR(64) NOT NULL,      -- 场景: DB_ANALYSIS, FILE_ANALYSIS...
    session_id UUID,                      -- 会话 ID
    sub_dir_id UUID,                      -- 隔离目录 ID (针对文件场景)
    user_id VARCHAR(64),                  -- 用户 ID

    -- 3. 异步生命周期与状态机
    status VARCHAR(32) NOT NULL DEFAULT 'INIT',
    -- 枚举: INIT -> RUNNING -> STREAMING -> SUCCESS / FAILED

    -- 4. 模型与 Token 统计
    provider VARCHAR(32) NOT NULL,        -- openai, deepseek, ollama...
    model_name VARCHAR(64) NOT NULL,      -- gpt-4o, deepseek-chat...
    call_type VARCHAR(32) NOT NULL,       -- ASYNC_CHAT, STREAM, EMBEDDING, TOOL_CALL

    prompt_tokens INT DEFAULT 0,
    completion_tokens INT DEFAULT 0,
    total_tokens INT DEFAULT 0,
    latency_ms BIGINT DEFAULT 0,          -- 总耗时 (ms)
    first_token_latency_ms BIGINT DEFAULT 0, -- 首 Token 延迟 (TTFT，流式打字机场景)

    -- 5. 载荷数据 (JSONB)
    request_payload JSONB NOT NULL DEFAULT '{}',   -- System Prompt, Messages, Tools
    response_payload JSONB DEFAULT '{}',           -- Model Reply, Reasoning, Tool Call Outputs
    error_message TEXT,                            -- 错误堆栈

    -- 6. 时间戳
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    start_time TIMESTAMP WITH TIME ZONE,
    end_time TIMESTAMP WITH TIME ZONE
);

-- SDK 自动维护的高效索引
CREATE INDEX IF NOT EXISTS idx_sdk_llm_trace_id ON sys_llm_invoke_log(trace_id);
CREATE INDEX IF NOT EXISTS idx_sdk_llm_session_status ON sys_llm_invoke_log(session_id, status);
CREATE INDEX IF NOT EXISTS idx_sdk_llm_created_at ON sys_llm_invoke_log(created_at DESC);