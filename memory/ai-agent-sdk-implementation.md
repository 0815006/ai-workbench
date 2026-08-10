---
name: ai-agent-sdk-implementation
description: ai-agent-sdk module created and ai-client-sdk upgraded with Tool Calling support
metadata:
  type: project
---

# ai-agent-sdk Implementation (2026-08-10)

## ai-client-sdk Upgrades (v1.1.0)
- **New models**: ToolDefinition, ToolCall, ToolChoice in `com.realapex.client.model`
- **Message extended**: added `toolCalls` field, `name` field, factory methods `assistantToolCall()`, `assistantWithToolCalls()`, `toolResult()`
- **Choice.Delta extended**: added `toolCalls` field for SSE streaming
- **AiRequest extended**: added `tools` and `toolChoice` fields
- **AiResponse extended**: added `hasToolCalls()`, `getToolCalls()`, `getText()`, `getFinishReason()`
- **Stream system**: StreamEvent sealed interface (TextChunk, ToolCallChunk, UsageEvent, Complete), StreamToolCallBuffer for incremental assembly
- **StreamListener upgraded**: added `onToolCallChunk()` and `onUsage()` default methods
- **AiClient upgraded**: new `generate(AiRequest)` method returning full AiResponse
- **DefaultAiClient**: Jackson fault-tolerance (ALLOW_UNQUOTED_CONTROL_CHARS, ALLOW_SINGLE_QUOTES, ALLOW_UNQUOTED_FIELD_NAMES), SSE tool call parsing, readTimeout for streams, Usage extraction
- **AiConfig**: added `readTimeout` field (default 30s)
- **RetryHandler**: added random jitter to backoff

## ai-agent-sdk New Module
Package: `com.realapex.agent`
- **tool/**: AgentTool<REQ,RESP> interface, SchemaGenerator (Jackson-based JSON Schema), ToolRegistry (thread-safe ConcurrentHashMap)
- **annotation/**: @Tool annotation for method-level tool declaration
- **execution/**: AgentRequest, AgentRunner (ReAct loop with virtual thread parallel tool execution + error self-healing), AgentStepResult, AgentResult
- **context/**: ContextTrimmer with tool message pairing rules
- **event/**: AgentEventListener (onStepStart/Finish, onToolStart/End, onChunk, onComplete)
- **exception/**: AgentMaxStepsExceededException
- **config/**: AgentProperties, ToolBeanPostProcessor (@Tool scanner), AgentAutoConfiguration

## Build Status
`mvn clean package` → BUILD SUCCESS (both modules)
JARs: ai-client-sdk-1.0.0-SNAPSHOT.jar, ai-agent-sdk-1.0.0-SNAPSHOT.jar

**Why:** Required to support ReAct agent orchestration with tool calling as specified in PRDs.
**How to apply:** ai-agent-sdk depends on ai-client-sdk. Configure ai-client-sdk first (API keys), then use AgentRunner via Spring Boot auto-config or manual construction.
