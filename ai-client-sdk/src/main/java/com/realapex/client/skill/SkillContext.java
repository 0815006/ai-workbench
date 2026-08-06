package com.realapex.client.skill;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 执行上下文。
 * <p>提供 Skill 执行期间的共享状态存储，支持跨步骤传递中间结果。</p>
 */
public class SkillContext {

    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    /**
     * 存入上下文属性。
     *
     * @param key   键
     * @param value 值
     */
    public void put(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 读取上下文属性。
     *
     * @param key 键
     * @param <T> 期望类型
     * @return 属性值，不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) attributes.get(key);
    }

    /**
     * 清理上下文。
     */
    public void clear() {
        attributes.clear();
    }
}
