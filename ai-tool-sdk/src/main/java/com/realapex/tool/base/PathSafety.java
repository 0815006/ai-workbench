package com.realapex.tool.base;

import java.nio.file.Path;

/**
 * 路径安全工具——沙箱路径解析与路径穿越防护。
 * <p>所有 {@code base} 文件/目录工具必须通过 {@link #resolveSafePath} 解析用户路径，
 * 强制将路径限定在 {@code baseDir} 沙箱内，防范 {@code ../../etc/passwd} 等越权操作。</p>
 *
 * <h3>防护原理</h3>
 * <pre>{@code
 * baseDir  = /home/workspace/project-a
 * userPath = ../../etc/passwd
 * resolved = /etc/passwd  → 不以 baseDir 开头 → 抛出 SecurityException
 * }</pre>
 */
public final class PathSafety {

    private PathSafety() {
    }

    /**
     * 在沙箱根目录内安全解析用户路径。
     * <p>将 {@code baseDir} 与 {@code userPath} 拼接并规范化后，校验结果是否仍位于
     * {@code baseDir} 之内；若发生路径穿越则抛出 {@link SecurityException}。</p>
     *
     * @param baseDir  沙箱根目录（绝对路径）
     * @param userPath 用户传入的相对/绝对路径
     * @return 规范化后的安全绝对路径
     * @throws SecurityException 路径穿越或越权时抛出
     */
    public static Path resolveSafePath(Path baseDir, String userPath) {
        if (userPath == null || userPath.isBlank()) {
            throw new SecurityException("路径不能为空");
        }

        Path base = baseDir.toAbsolutePath().normalize();
        Path resolved = base.resolve(userPath).normalize();

        if (!resolved.startsWith(base)) {
            throw new SecurityException("Path traversal blocked: " + userPath);
        }
        return resolved;
    }

    /**
     * 校验路径是否位于沙箱根目录内（不拼接，仅校验）。
     *
     * @param baseDir 沙箱根目录
     * @param path    待校验路径
     * @return true 表示在沙箱内
     */
    public static boolean isWithinBaseDir(Path baseDir, Path path) {
        Path base = baseDir.toAbsolutePath().normalize();
        Path target = path.toAbsolutePath().normalize();
        return target.startsWith(base);
    }
}