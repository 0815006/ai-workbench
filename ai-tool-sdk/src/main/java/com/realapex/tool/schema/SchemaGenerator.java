package com.realapex.tool.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.realapex.tool.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Jackson 的 JSON Schema 自动生成器。
 * <p>将 Java 类（Record、DTO、基本类型）的字段结构转换为 OpenAI Function Calling
 * 兼容的 JSON Schema（parameters 字段）。支持读取 {@link ToolParam @ToolParam}
 * 注解以生成更精确的字段描述和必填标记。</p>
 *
 * <h3>类型映射</h3>
 * <ul>
 *   <li>{@code String} → {"type": "string"}</li>
 *   <li>{@code int/Integer/long/Long} → {"type": "integer"}</li>
 *   <li>{@code double/Double/float/Float} → {"type": "number"}</li>
 *   <li>{@code boolean/Boolean} → {"type": "boolean"}</li>
 *   <li>{@code Enum} → {"type": "string", "enum": [...]}</li>
 *   <li>{@code List/Array} → {"type": "array", "items": {...}}</li>
 *   <li>{@code Map} → {"type": "object"}</li>
 *   <li>{@code Record/DTO} → {"type": "object", "properties": {...}}</li>
 * </ul>
 *
 * <h3>线程安全</h3>
 * <p>内部使用 {@link ConcurrentHashMap} 缓存已生成的 Schema，线程安全。</p>
 */
@Slf4j
public class SchemaGenerator {

    private final ObjectMapper objectMapper;
    private final Map<Class<?>, Map<String, Object>> cache = new ConcurrentHashMap<>();

    public SchemaGenerator() {
        this.objectMapper = JsonMapper.builder()
                .configure(SerializationFeature.INDENT_OUTPUT, false)
                .build();
    }

    /**
     * 为指定 Java 类型生成 JSON Schema。
     *
     * @param clazz Java 类型
     * @return JSON Schema Map（可直接传给 ToolDefinition.of()）
     */
    public Map<String, Object> generate(Class<?> clazz) {
        return cache.computeIfAbsent(clazz, this::doGenerate);
    }

    /**
     * 清除缓存。
     */
    public void clearCache() {
        cache.clear();
    }

    // ==================== 内部实现 ====================

    private Map<String, Object> doGenerate(Class<?> clazz) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        // 遍历所有字段
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            String fieldName = field.getName();
            Class<?> fieldType = field.getType();
            Map<String, Object> fieldSchema = buildFieldSchema(field.getGenericType());

            // 读取 @ToolParam 注解
            ToolParam toolParam = field.getAnnotation(ToolParam.class);
            if (toolParam != null) {
                if (!toolParam.description().isEmpty()) {
                    fieldSchema.put("description", toolParam.description());
                }
                if (toolParam.required()) {
                    if (!required.contains(fieldName)) {
                        required.add(fieldName);
                    }
                }
            } else {
                // 无 @ToolParam 时，非基本类型默认 required
                if (!fieldType.isPrimitive()) {
                    required.add(fieldName);
                }
            }

            properties.put(fieldName, fieldSchema);
        }

        // Record 的 accessor 方法
        if (clazz.isRecord()) {
            for (Method method : clazz.getMethods()) {
                if (method.getParameterCount() == 0
                        && !method.isSynthetic()
                        && !method.getDeclaringClass().equals(Object.class)
                        && !"toString".equals(method.getName())
                        && !"hashCode".equals(method.getName())) {
                    String name = method.getName();
                    if (!properties.containsKey(name)) {
                        Class<?> returnType = method.getReturnType();
                        Map<String, Object> fieldSchema = buildFieldSchema(method.getGenericReturnType());

                        // Record component 的 @ToolParam
                        try {
                            Field recordField = clazz.getDeclaredField(name);
                            ToolParam toolParam = recordField.getAnnotation(ToolParam.class);
                            if (toolParam != null) {
                                if (!toolParam.description().isEmpty()) {
                                    fieldSchema.put("description", toolParam.description());
                                }
                                if (toolParam.required()) {
                                    required.add(name);
                                }
                            }
                        } catch (NoSuchFieldException ignored) {
                            if (!returnType.isPrimitive()) {
                                required.add(name);
                            }
                        }

                        properties.put(name, fieldSchema);
                        if (!required.contains(name) && !returnType.isPrimitive()) {
                            required.add(name);
                        }
                    }
                }
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        schema.put("additionalProperties", false);

        log.debug("Generated JSON Schema for {}: {} properties, {} required",
                clazz.getSimpleName(), properties.size(), required.size());
        return schema;
    }

    /**
     * 根据 Java 类型构建 JSON Schema 字段定义。
     */
    private Map<String, Object> buildFieldSchema(Type genericType) {
        Map<String, Object> schema = new LinkedHashMap<>();

        if (genericType instanceof ParameterizedType pt) {
            Class<?> rawType = (Class<?>) pt.getRawType();

            if (List.class.isAssignableFrom(rawType)) {
                schema.put("type", "array");
                Type itemType = pt.getActualTypeArguments()[0];
                schema.put("items", buildFieldSchema(itemType));
                return schema;
            }

            if (Map.class.isAssignableFrom(rawType)) {
                schema.put("type", "object");
                return schema;
            }
        }

        Class<?> clazz;
        if (genericType instanceof Class<?> c) {
            clazz = c;
        } else if (genericType instanceof ParameterizedType pt) {
            clazz = (Class<?>) pt.getRawType();
        } else {
            schema.put("type", "string");
            return schema;
        }

        if (clazz == String.class || clazz == Character.class || clazz == char.class) {
            schema.put("type", "string");
        } else if (clazz == Integer.class || clazz == int.class
                || clazz == Long.class || clazz == long.class
                || clazz == Short.class || clazz == short.class
                || clazz == Byte.class || clazz == byte.class) {
            schema.put("type", "integer");
        } else if (clazz == Double.class || clazz == double.class
                || clazz == Float.class || clazz == float.class
                || clazz == java.math.BigDecimal.class) {
            schema.put("type", "number");
        } else if (clazz == Boolean.class || clazz == boolean.class) {
            schema.put("type", "boolean");
        } else if (clazz.isEnum()) {
            schema.put("type", "string");
            List<String> enumValues = new ArrayList<>();
            for (Object enumConst : clazz.getEnumConstants()) {
                enumValues.add(enumConst.toString());
            }
            schema.put("enum", enumValues);
        } else if (clazz == LocalDate.class || clazz == LocalDateTime.class
                || clazz == java.util.Date.class) {
            schema.put("type", "string");
            schema.put("format", "date-time");
        } else {
            return doGenerate(clazz);
        }

        return schema;
    }
}
