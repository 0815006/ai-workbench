package com.realapex.tool.base.system;

import com.realapex.tool.base.BaseToolConfig;
import com.realapex.tool.contract.AgentTool;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 获取环境信息工具（{@code getEnvInfo}）。
 * <p>返回当前 Agent 运行的 OS 类型、工作目录、Java 版本及环境变量白名单，
 * 帮助 Agent 感知运行环境并做出适配（如 Windows 用 cmd、Linux 用 sh）。</p>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>环境变量白名单：仅暴露 {@code envWhitelist} 中配置的变量，防止敏感信息泄露</li>
 * </ul>
 */
@Slf4j
public class GetEnvInfoTool implements AgentTool<GetEnvInfoTool.Request, String> {

    private final BaseToolConfig config;

    public GetEnvInfoTool(BaseToolConfig config) {
        this.config = config;
    }

    @Override
    public String name() {
        return "getEnvInfo";
    }

    @Override
    public String description() {
        return "获取当前 Agent 运行环境信息：OS 类型、工作目录、Java 版本、"
                + "环境变量白名单。无参数，直接调用即可。";
    }

    @Override
    public Class<Request> requestClass() {
        return Request.class;
    }

    @Override
    public String execute(Request request) {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("os.name", System.getProperty("os.name"));
        info.put("os.arch", System.getProperty("os.arch"));
        info.put("os.version", System.getProperty("os.version"));
        info.put("java.version", System.getProperty("java.version"));
        info.put("java.vendor", System.getProperty("java.vendor"));
        info.put("user.dir", config.effectiveBaseDir().toString());
        info.put("file.separator", System.getProperty("file.separator"));

        StringBuilder sb = new StringBuilder("=== 环境信息 ===\n");
        info.forEach((k, v) -> sb.append(k).append(": ").append(v).append('\n'));

        sb.append("\n=== 环境变量白名单 ===\n");
        for (String key : config.getEnvWhitelist()) {
            String value = System.getenv(key);
            sb.append(key).append(": ").append(value == null ? "(未设置)" : value).append('\n');
        }

        log.debug("getEnvInfo: os={}, java={}, dir={}",
                info.get("os.name"), info.get("java.version"), info.get("user.dir"));
        return sb.toString();
    }

    /**
     * 环境信息请求参数（无参工具）。
     */
    public record Request() {
    }
}