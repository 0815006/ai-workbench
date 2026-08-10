package com.realapex.agent.config;

import com.realapex.tool.annotation.Tool;
import com.realapex.agent.tool.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotationUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Spring Bean 后置处理器——自动扫描 {@link Tool @Tool} 注解方法并注册到 {@link ToolRegistry}。
 * <p>在 Spring 容器初始化每个 Bean 后，扫描其 public 方法上是否有 @Tool 注解，
 * 若有则自动包装为 AgentTool 适配器并注册到全局 ToolRegistry。</p>
 *
 * <h3>扫描规则</h3>
 * <ul>
 *   <li>仅扫描 public 方法</li>
 *   <li>方法第一个参数类型作为工具的 requestClass（无参方法使用 Void.class）</li>
 *   <li>方法名或注解的 {@code name} 属性作为工具名称</li>
 *   <li>同名校验：若工具名已注册则抛出异常</li>
 * </ul>
 */
@Slf4j
public class ToolBeanPostProcessor implements BeanPostProcessor {

    private final ToolRegistry toolRegistry;

    public ToolBeanPostProcessor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> clazz = bean.getClass();
        // 跳过 JDK 内部类、代理类
        if (clazz.getName().startsWith("java.") || clazz.getName().startsWith("jdk.")) {
            return bean;
        }

        Method[] methods = clazz.getMethods();
        for (Method method : methods) {
            // 跳过 Object 基类方法
            if (method.getDeclaringClass() == Object.class) {
                continue;
            }

            Tool toolAnnotation = AnnotationUtils.findAnnotation(method, Tool.class);
            if (toolAnnotation == null) {
                continue;
            }

            // 确保方法是 public
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                log.warn("@Tool 方法 {} ({}.{}) 不是 public，已跳过",
                        toolAnnotation.description(), clazz.getSimpleName(), method.getName());
                continue;
            }

            String toolName = toolAnnotation.name();
            if (toolName == null || toolName.isBlank()) {
                toolName = method.getName();
            }

            String description = toolAnnotation.description();
            if (description == null || description.isBlank()) {
                log.warn("@Tool 方法 {}.{} 缺少 description，使用默认描述",
                        clazz.getSimpleName(), method.getName());
                description = "执行 " + toolName;
            }

            try {
                toolRegistry.registerMethodTool(toolName, description, method, bean);
                log.info("自动注册 @Tool: {} -> {}.{}",
                        toolName, clazz.getSimpleName(), method.getName());
            } catch (IllegalArgumentException e) {
                log.error("@Tool 注册失败 [{}.{}]: {}", clazz.getSimpleName(), method.getName(), e.getMessage());
                throw e;
            }
        }

        return bean;
    }
}
