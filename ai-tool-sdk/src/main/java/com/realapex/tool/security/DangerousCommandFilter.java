package com.realapex.tool.security;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 高危指令过滤器——检测工具入参中的危险模式并拒绝执行。
 * <p>优先级: 20（在参数校验之后、超时控制之前）</p>
 *
 * <h3>检测模式（大小写不敏感）</h3>
 * <ul>
 *   <li>SQL 注入/破坏：DROP TABLE, DELETE FROM, TRUNCATE, ALTER TABLE</li>
 *   <li>系统命令：rm -rf, shutdown, reboot, chmod 777</li>
 *   <li>代码注入：eval(), exec(), Runtime.getRuntime()</li>
 * </ul>
 */
@Slf4j
public class DangerousCommandFilter implements ToolSecurityInterceptor {

    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            // SQL 危险操作
            Pattern.compile("\\bDROP\\s+(TABLE|DATABASE|SCHEMA)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bDELETE\\s+FROM\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bTRUNCATE\\s+(TABLE\\s+)?", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bALTER\\s+(TABLE|DATABASE)\\b.*\\bDROP\\b", Pattern.CASE_INSENSITIVE),

            // 系统危险命令
            Pattern.compile("\\brm\\s+-rf\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bshutdown\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\breboot\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bchmod\\s+777\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bmkfs\\.", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdd\\s+if=", Pattern.CASE_INSENSITIVE),

            // 代码注入
            Pattern.compile("\\beval\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bexec\\s*\\(", Pattern.CASE_INSENSITIVE),
            Pattern.compile("Runtime\\.getRuntime\\(\\)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ProcessBuilder", Pattern.CASE_INSENSITIVE),

            // 路径遍历
            Pattern.compile("\\.\\./\\.\\./", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\\\\\.\\.\\\\\\.\\.\\\\", Pattern.CASE_INSENSITIVE)
    );

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public void before(String toolName, Object request) throws SecurityException {
        if (request == null) {
            return;
        }

        // 检查所有 String 字段
        Class<?> clazz = request.getClass();
        if (clazz.isPrimitive() || Number.class.isAssignableFrom(clazz)) {
            return;
        }

        if (request instanceof String s) {
            checkString(toolName, "<root>", s);
            return;
        }

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isSynthetic() || java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            field.setAccessible(true);
            try {
                Object value = field.get(request);
                if (value instanceof String s) {
                    checkString(toolName, field.getName(), s);
                }
            } catch (IllegalAccessException e) {
                log.warn("无法访问字段 {}: {}", field.getName(), e.getMessage());
            }
        }
    }

    private void checkString(String toolName, String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return;
        }

        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(value).find()) {
                String msg = String.format(
                        "工具 [%s] 参数 '%s' 包含危险指令，已被拦截。匹配模式: %s",
                        toolName, fieldName, pattern.pattern());
                log.warn(msg);
                throw new SecurityException(msg);
            }
        }
    }
}
