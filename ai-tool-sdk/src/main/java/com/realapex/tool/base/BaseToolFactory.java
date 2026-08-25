package com.realapex.tool.base;

import com.realapex.tool.base.dir.GetTreeTool;
import com.realapex.tool.base.dir.MakeDirTool;
import com.realapex.tool.base.dir.ReadDirTool;
import com.realapex.tool.base.file.EditFileTool;
import com.realapex.tool.base.file.ReadFileTool;
import com.realapex.tool.base.file.WriteFileTool;
import com.realapex.tool.base.file.WriteMarkdownTool;
import com.realapex.tool.base.search.SearchContentTool;
import com.realapex.tool.base.search.SearchFilesTool;
import com.realapex.tool.base.system.ExecCommandTool;
import com.realapex.tool.base.system.FetchUrlTool;
import com.realapex.tool.base.system.GetEnvInfoTool;
import com.realapex.tool.contract.AgentTool;

import java.util.List;

/**
 * 基础工具工厂——一键挂载标准工具组（Tool Sets / Bundles）。
 * <p>对应 PRD 中的 {@code createFileSystemTools} / {@code createSystemTools}，
 * 宿主应用无需逐个 {@code new} 工具，直接调用工厂方法即可获得带沙箱防护的完整工具组。</p>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * BaseToolConfig config = BaseToolConfig.builder()
 *         .baseDir(Path.of("/home/workspace/project-a"))
 *         .allowedCommands(List.of("npm test", "mvn compile", "git status"))
 *         .build();
 *
 * // 一键挂载文件系统工具组（9 个工具）
 * List<AgentTool<?, ?>> fsTools = BaseToolFactory.createFileSystemTools(config);
 *
 * // 一键挂载系统工具组（3 个工具）
 * List<AgentTool<?, ?>> systemTools = BaseToolFactory.createSystemTools(config);
 * }</pre>
 */
public final class BaseToolFactory {

    private BaseToolFactory() {
    }

    /**
     * 创建文件系统工具组（{@code createFileSystemTools}）。
     * <p>包含 9 个工具：readFile、writeFile、editFile、writeMarkdown、\n
     * readDir、getTree、makeDir、searchFiles、searchContent。</p>
     *
     * @param config 基础工具配置（沙箱根目录、截断上限等）
     * @return 文件系统工具列表
     */
    public static List<AgentTool<?, ?>> createFileSystemTools(BaseToolConfig config) {
        return List.of(
                new ReadFileTool(config),
                new WriteFileTool(config),
                new EditFileTool(config),
                new WriteMarkdownTool(config),
                new ReadDirTool(config),
                new GetTreeTool(config),
                new MakeDirTool(config),
                new SearchFilesTool(config),
                new SearchContentTool(config)
        );
    }

    /**
     * 创建系统工具组（{@code createSystemTools}）。
     * <p>包含 3 个工具：execCommand、fetchUrl、getEnvInfo。</p>
     *
     * @param config 基础工具配置（命令白名单、超时等）
     * @return 系统工具列表
     */
    public static List<AgentTool<?, ?>> createSystemTools(BaseToolConfig config) {
        return List.of(
                new ExecCommandTool(config),
                new FetchUrlTool(config),
                new GetEnvInfoTool(config)
        );
    }
}