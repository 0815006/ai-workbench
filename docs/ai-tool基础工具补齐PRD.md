
针对 Agent 自动化工作流（特别是编码、文档生成、代码排查、系统运维等 Coding Agent / Task Agent 场景），`tool-sdk` 的标准库（`std/` 或 `built-in/`）应当提供一套**开箱即用、带安全沙箱防护**的基础工具集。

参照 Vercel AI SDK (Core Tools) 以及 Claude Computer Use / AutoGPT 等业界标准范式，我们将这些开箱即用工具划分为 **4 大核心模块，共计 12 个基础工具**：

---

### 一、 基础工具开箱即用清单 (Standard Tools Catalog)

```
tool-sdk/std
├── file/           # 1. 文件与内容读写模块
├── dir/            # 2. 目录与结构探测模块
├── search/         # 3. 内容与代码检索模块
└── system/         # 4. 系统受控执行模块

```

#### 1. 文件与内容读写模块 (`std/file`)

> **核心职责**：解决 Agent 对文本、代码、配置及 Markdown 文件的精准读写与更新。

| 工具名称               | 标识符 (Tool Name)            | 核心功能                                                                 | 参数输入 (Input Schema)                                   | 返回输出 (Output)             |
| ---------------------- | ----------------------------- | ------------------------------------------------------------------------ | --------------------------------------------------------- | ----------------------------- |
| **读取文件**     | `readFile`                  | 读取文本/代码文件内容。支持指定编码或分行/分段读取（防大文件爆 Token）。 | `filePath`, `encoding?`, `startLine?`, `endLine?` | 文件内容字符串 + 行数统计     |
| **写入文件**     | `writeFile`                 | 创建新文件或覆盖写入完整内容（适用于全量生成代码/配置）。                | `filePath`, `content`                                 | 写入成功状态 + 文件 Byte 大小 |
| **局部增量更新** | `editFile` / `applyPatch` | 精准替换文件中的某段代码/文本（避免小修改却重写整个大文件）。            | `filePath`, `oldString`, `newString`                | 替换成功状态 + 匹配行号       |
| **写 Markdown**  | `writeMarkdown`             | 结构化写入 Markdown，自动格式化并支持追加模式（适用于生成文档、报告）。  | `filePath`, `content`, `appendMode?`                | 写入/追加结果                 |

---

#### 2. 目录与结构探测模块 (`std/dir`)

> **核心职责**：帮助 Agent 快速建立对项目或工作区文件结构的整体认知。

| 工具名称             | 标识符 (Tool Name)        | 核心功能                                                             | 参数输入 (Input Schema)                    | 返回输出 (Output)              |
| -------------------- | ------------------------- | -------------------------------------------------------------------- | ------------------------------------------ | ------------------------------ |
| **读取目录**   | `readDir` / `listDir` | 列出指定目录下的文件与子目录（可配置递归深度、过滤隐藏文件）。       | `dirPath`, `recursive?`, `maxDepth?` | 文件/文件夹名称列表及类型      |
| **获取目录树** | `getTree`               | 以树状图结构输出当前项目目录（包含过滤配置如 Ignore Node_modules）。 | `dirPath`, `ignorePatterns?`           | 树状文本 (如`tree` 命令输出) |
| **创建目录**   | `makeDir`               | 递归创建文件夹路径 (类似`mkdir -p`)。                              | `dirPath`                                | 创建状态                       |

---

#### 3. 内容与代码检索模块 (`std/search`)

> **核心职责**：在庞大的代码库或工作区中快速定位相关代码片段（代替全量读取，极省 Token）。

| 工具名称                 | 标识符 (Tool Name)           | 核心功能                                                           | 参数输入 (Input Schema)                   | 返回输出 (Output)                |
| ------------------------ | ---------------------------- | ------------------------------------------------------------------ | ----------------------------------------- | -------------------------------- |
| **文件名通配搜索** | `searchFiles` / `glob`   | 基于 Glob 表达式按文件名搜索（如`**/*.java`, `src/**/*.ts`）。 | `pattern`, `baseDir?`                 | 匹配到的相对路径列表             |
| **文本/代码搜索**  | `searchContent` / `grep` | 基于关键词或正则表达式，全文检索匹配的代码行（对标`ripgrep`）。  | `query`, `isRegex?`, `filePattern?` | 包含文件名、行号、匹配文本的数组 |

---

#### 4. 系统受控执行模块 (`std/system`)

> **核心职责**：让 Agent 能够运行构建脚本、单元测试、Linter 或 Shell 指令，完成“改代码 -> 跑测试 -> 报错”闭环。

| 工具名称                  | 标识符 (Tool Name)                | 核心功能                                                            | 参数输入 (Input Schema)                       | 返回输出 (Output)                    |
| ------------------------- | --------------------------------- | ------------------------------------------------------------------- | --------------------------------------------- | ------------------------------------ |
| **受控 Shell 命令** | `execCommand` / `runTerminal` | （高危受控）在沙箱目录中执行 Shell 指令（带超时断开、命令黑名单）。 | `command`, `timeoutMs?`, `envVars?`     | `stdout`, `stderr`, `exitCode` |
| **网络 HTTP 请求**  | `fetchUrl` / `httpRequest`    | 简单的 HTTP GET/POST 客户端，允许 Agent 抓取网页或调用外部 API。    | `url`, `method?`, `headers?`, `body?` | HTTP Status, Response Body           |
| **获取环境信息**    | `getEnvInfo`                    | 获取当前 Agent 运行的 OS 类型、工作目录、环境变量白名单。           | 无                                            | OS 平台、Node/Java 版本、工作路径    |

---

### 二、 基础工具必须具备的“三大安全防御机制”

基础工具封装在 `tool-sdk/std` 中时，**绝不能**只简单调用底层的 `fs.readFile` 或 `child_process.exec`，必须内置以下防御机制：

#### 1. 路径穿越与沙箱隔离 (Path Traversal & Sandboxing)

所有文件/目录工具必须强制注入 `baseDir`（沙箱根目录），防范 Agent 产生 `../../etc/passwd` 等越权操作。

```typescript
// 伪代码示例：标准库内部统一校验
function resolveSafePath(baseDir: string, userPath: string): string {
  const resolved = path.resolve(baseDir, userPath);
  if (!resolved.startsWith(path.resolve(baseDir))) {
    throw new SecurityException(`Path traversal blocked: ${userPath}`);
  }
  return resolved;
}

```

#### 2. 命令黑名单与超时强杀 (Command Safety)

`execCommand` 工具必须默认具备黑名单机制（如禁止 `rm -rf /`、`shutdown`、`sudo` 等），并限制最大执行时间（默认如 30 秒），防止死循环命令拖垮服务端。

#### 3. Token 爆炸保护 (Output Truncation)

当 `readFile` 读到一个 50MB 的日志文件，或 `execCommand` 输出了 10 万行日志时，直接返回给 LLM 会导致 Context Window 瞬间爆满。

* **防护策略**：所有 `std` 工具的返回结果，必须内置截断逻辑（如单个工具返回结果强制限制最大 20,000 字符或 4,000 Token），超出部分自动替换为 `[...Content truncated, Total lines: 5000...]`。

---

### 三、 在应用层如何一键挂载基础工具包？

得益于在 `tool-sdk/std` 中的集中封装，宿主应用在构建 Agent 时，不需要逐个 `new ReadFileTool()`，而是可以通过工具组（Tool Sets / Bundles）一键加载：

```typescript
import { Agent } from '@your-sdk/agent-sdk';
import { createFileSystemTools, createSystemTools } from '@your-sdk/tool-sdk/std';

// 1. 一键初始化带有沙箱保护的文件工具组
const fsTools = createFileSystemTools({
  baseDir: '/home/workspace/project-a',
  maxFileSize: '2MB' // 单文件读取限制
});

// 2. 一键初始化受控的命令工具组
const systemTools = createSystemTools({
  baseDir: '/home/workspace/project-a',
  allowedCommands: ['npm test', 'mvn compile', 'git status'], // 严格指令白名单
  timeoutMs: 30000
});

// 3. 喂给 Agent
const agent = new Agent({
  model: 'gpt-4o',
  tools: [
    ...fsTools,       # 包含 readFile, writeFile, editFile, writeMarkdown, readDir, getTree, searchFiles, searchContent
    ...systemTools,   # 包含 execCommand, fetchUrl
    myBusinessTool    # 应用层自定义的业务工具
  ]
});

```

### 总结

将这 **12 个基础工具** 作为标准库落到 `tool-sdk/std` 中：

1. 完全覆盖了 **L2 层的“跨文件读写与命令执行”** 与 **“自主规划排查”** 所需的底座能力；
2. 彻底屏蔽了文件越权、Token 爆满、命令超时等安全死角；
3. 让应用层能以 `createFileSystemTools({ baseDir })` 的极简方式一键接入。
