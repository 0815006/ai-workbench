package com.realapex.tool.security;

import com.realapex.tool.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;

/**
 * 参数校验拦截器——基于 {@link ToolParam @ToolParam} 注解校验工具入参。
 * <p>优先级: 10（最早执行）</p>
 */
@Slf4j
public class ParamValidator implements ToolSecurityInterceptor {

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public void before(String toolName, Object request) throws SecurityException {
        if (request == null) {
            return; // 无参工具跳过
        }

        Class<?> clazz = request.getClass();

        // 跳过基本类型和 String
        if (clazz.isPrimitive() || clazz == String.class
                || Number.class.isAssignableFrom(clazz)) {
            return;
        }

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            ToolParam toolParam = field.getAnnotation(ToolParam.class);
            if (toolParam == null || !toolParam.required()) {
                continue;
            }

            field.setAccessible(true);
            try {
                Object value = field.get(request);
                if (value == null) {
                    throw new SecurityException(
                            String.format("工具 [%s] 参数校验失败: 必填字段 '%s' 为 null",
                                    toolName, field.getName()));
                }
                if (value instanceof String s && s.isBlank()) {
                    throw new SecurityException(
                            String.format("工具 [%s] 参数校验失败: 必填字段 '%s' 为空字符串",
                                    toolName, field.getName()));
                }
            } catch (IllegalAccessException e) {
                log.warn("无法访问字段 {}: {}", field.getName(), e.getMessage());
            }
        }

        log.debug("参数校验通过: tool={}", toolName);
    }
}
