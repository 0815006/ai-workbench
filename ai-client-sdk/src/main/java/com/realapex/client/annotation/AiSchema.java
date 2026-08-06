package com.realapex.client.annotation;

import java.lang.annotation.*;

/**
 * 标记一个类用于 AI 结构化输出的 JSON Schema 生成。
 * <p>配合 {@code generateObject} 使用，可自动将标注类的字段信息
 * 注入 Prompt 以约束大模型输出格式。</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiSchema {

    /** Schema 描述（注入 system prompt） */
    String description() default "";
}
