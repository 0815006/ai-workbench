package com.realapex.tool.base.system;

import com.realapex.tool.annotation.ToolParam;
import com.realapex.tool.base.BaseToolConfig;
import com.realapex.tool.base.OutputTruncator;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.security.DangerousCommandFilter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 受控 Shell 命令执行工具（{@code execCommand} / {@code runTerminal}）。
 * <p>在沙箱目录中执行 Shell 指令，完成"改代码 → 跑测试 → 报错"闭环。
 * 内置三大安全防御：命令黑名单、超时强杀、输出截断。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>危险命令过滤：复用 {@link DangerousCommandFilter} 的 11 种危险模式正则检测</li>
 *   <li>命令白名单：配置了 {@code allowedCommands} 时，命令必须以白名单前缀开头</li>
 *   <li>超时强杀：默认 30 秒，超时强制销毁进程（PRD 要求）</li>
 *   <li>Token 爆炸防护：stdout/stderr 经 {@link OutputTruncator#truncate} 截断</li>
 * </ul>
 */
@Slf4j
public class ExecCommandTool implements AgentTool<ExecCommandTool.Request, String> {

    private final BaseToolConfig config;
    private final DangerousCommandFilter commandFilter = new DangerousCommandFilter();

    public ExecCommandTool(BaseToolConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "execCommand";
    }

    @Override
    public String description() {
        return "在沙箱目录中执行受控 Shell 命令（如 mvn compile、npm test、git status）。"
                + "内置危险命令黑名单、超时强杀（默认 30s）与输出截断。"
                + "命令必须位于沙箱根目录内执行。";
    }

    @Override
    public Class<Request> requestClass() {
        return Request.class;
    }

    @Override
    public String execute(Request request) throws Exception {
        if (request.command() == null || request.command().isBlank()) {
            throw new IllegalArgumentException("command 不能为空");
        }

        // 1. 危险命令过滤（复用安全拦截器）
        commandFilter.before(name(), request);

        // 2. 命令白名单校验
        List<String> allowed = config.getAllowedCommands();
        if (allowed != null && !allowed.isEmpty()) {
            boolean matched = allowed.stream()
                    .anyMatch(prefix -> request.command().trim().startsWith(prefix.trim()));
            if (!matched) {
                throw new SecurityException(
                        "命令不在白名单内，已拒绝执行: " + request.command()
                                + "（白名单: " + allowed + "）");
            }
        }

        // 3. 超时执行
        long timeoutMs = request.timeoutMs() != null
                ? request.timeoutMs()
                : config.getCommandTimeoutMs();

        ProcessBuilder pb = new ProcessBuilder();
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            pb.command("cmd.exe", "/c", request.command());
        } else {
            pb.command("/bin/sh", "-c", request.command());
        }
        pb.directory(config.effectiveBaseDir().toFile());
        if (request.envVars() != null) {
            pb.environment().putAll(request.envVars());
        }

        Process process = pb.start();
        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException(String.format(
                    "命令执行超时（%d ms），已强制终止: %s", timeoutMs, request.command()));
        }

        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.exitValue();

        String result = String.format("exitCode: %d%n--- stdout ---%n%s%n--- stderr ---%n%s",
                exitCode, stdout, stderr);
        log.info("execCommand: {} (exit={}, {}ms)", request.command(), exitCode, timeoutMs);
        return OutputTruncator.truncate(result, config.getMaxOutputChars());
    }

    /**
     * 受控命令执行请求参数。
     *
     * @param command   待执行的 Shell 命令
     * @param timeoutMs 超时毫秒（可选，默认 30000）
     * @param envVars   附加环境变量（可选）
     */
    public record Request(
            @ToolParam(description = "待执行的 Shell 命令", required = true)
            String command,

            @ToolParam(description = "超时毫秒（可选，默认 30000）", required = false)
            Long timeoutMs,

            @ToolParam(description = "附加环境变量（可选）", required = false)
            java.util.Map<String, String> envVars
    ) {
    }
}