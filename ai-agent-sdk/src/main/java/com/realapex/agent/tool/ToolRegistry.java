package com.realapex.agent.tool;

import com.realapex.client.model.ToolDefinition;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.schema.SchemaGenerator;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程安全的工具注册表。
 * <p>支持按 {@code tool_name} 进行路由查找、动态挂载/卸载，
 * 以及批量导出 OpenAI 兼容的 {@link ToolDefinition} 列表。</p>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * ToolRegistry registry = new ToolRegistry(schemaGenerator);
 * registry.register(myTool);
 * AgentTool<?,?> tool = registry.get("get_explain_plan");
 * }</pre>
 */
@Slf4j
public class ToolRegistry {

    private final SchemaGenerator schemaGenerator;
    private final Map<String, AgentTool<?, ?>> tools = new ConcurrentHashMap<>();

    public ToolRegistry(SchemaGenerator schemaGenerator) {
        this.schemaGenerator = schemaGenerator;
    }

    /**
     * 注册一个工具实例。
     *
     * @param tool 工具实例
     * @throws IllegalArgumentException 同名工具已存在时抛出
     */
    public void register(AgentTool<?, ?> tool) {
        String name = tool.name();
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("Tool 名称冲突: " + name + " 已注册");
        }
        tools.put(name, tool);
        log.debug("注册 Tool: {} -> {}", name, tool.getClass().getSimpleName());
    }

    /**
     * 注册一个由 @Tool 注解方法包装的工具。
     *
     * @param name        工具名称
     * @param description 工具描述
     * @param method      目标方法
     * @param bean        方法所属 Bean 实例
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void registerMethodTool(String name, String description, Method method, Object bean) {
        registerMethodTool(name, description, method, bean, false);
    }

    /**
     * 注册一个由 @Tool 注解方法包装的工具（支持 HITL 审批标记）。
     *
     * @param name              工具名称
     * @param description       工具描述
     * @param method            目标方法
     * @param bean              方法所属 Bean 实例
     * @param requiresApproval  是否需要人工审批（HITL）
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void registerMethodTool(String name, String description, Method method, Object bean,
                                   boolean requiresApproval) {
        if (tools.containsKey(name)) {
            throw new IllegalArgumentException("Tool 名称冲突: " + name + " 已注册");
        }
        // 创建匿名 AgentTool 适配器
        Class<?>[] paramTypes = method.getParameterTypes();
        AgentTool<?, ?> adapter = new AgentTool() {
            @Override
            public String name() { return name; }

            @Override
            public String description() { return description; }

            @Override
            public Class<?> requestClass() {
                return paramTypes.length > 0 ? paramTypes[0] : Void.class;
            }

            @Override
            public boolean requiresApproval() { return requiresApproval; }

            @Override
            public Object execute(Object request) throws Exception {
                if (paramTypes.length == 0) {
                    return method.invoke(bean);
                }
                return method.invoke(bean, request);
            }
        };
        tools.put(name, adapter);
        log.debug("注册 @Tool 方法: {} -> {}.{} (requiresApproval={})",
                name, bean.getClass().getSimpleName(), method.getName(), requiresApproval);
    }

    /**
     * 卸载指定工具。
     *
     * @param name 工具名称
     * @return 被卸载的工具实例，不存在返回 null
     */
    public AgentTool<?, ?> unregister(String name) {
        AgentTool<?, ?> removed = tools.remove(name);
        if (removed != null) {
            log.debug("卸载 Tool: {}", name);
        }
        return removed;
    }

    /**
     * 按名称查找工具。
     *
     * @param name 工具名称
     * @return 工具实例，不存在返回 null
     */
    public AgentTool<?, ?> get(String name) {
        return tools.get(name);
    }

    /**
     * 获取所有已注册工具。
     *
     * @return 工具列表（不可变快照）
     */
    public List<AgentTool<?, ?>> getAll() {
        return List.copyOf(tools.values());
    }

    /**
     * 导出所有工具为 OpenAI 兼容的 ToolDefinition 列表。
     * <p>供 AgentRunner 直接注入 AiRequest.tools。</p>
     *
     * @return ToolDefinition 列表
     */
    public List<ToolDefinition> getToolDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (AgentTool<?, ?> tool : tools.values()) {
            Map<String, Object> schema = schemaGenerator.generate(tool.requestClass());
            definitions.add(ToolDefinition.of(tool.name(), tool.description(), schema));
        }
        return definitions;
    }

    /**
     * 当前注册的工具数量。
     *
     * @return 工具数
     */
    public int size() {
        return tools.size();
    }

    /**
     * 是否为空。
     *
     * @return true 如果没有注册任何工具
     */
    public boolean isEmpty() {
        return tools.isEmpty();
    }
}
