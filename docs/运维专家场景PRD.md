在确定了 **`ai-client-sdk`（通信）+ `ai-tool-sdk`（工具/契约/安全）+ `ai-agent-sdk`（编排/状态机）** 这底层“铁三角”之后，**`ai-scenario-ops-sdk`（运维专家场景套件）** 的架构定位和设计变得极其清晰、洗练且具有很强的工程美感。

在这个全新基座下，`ai-scenario-ops-sdk` 的本质是：**将 SRE / DBA / DevOps 的专家诊断经验（Prompts + Workflows + 结构化出参）代码化，并通过依赖 `ai-tool-sdk` 的契约来“装载”具体的领域工具。**

我们围绕运维（Ops）从“故障排查（Troubleshooting）”、“性能调优（Tuning）”、“变更风险（Release）”到“安全防御（Security）”**的全生命周期，重新梳理出 `ai-scenario-ops-sdk` 涵盖的 **5 大运维场景子域**，以及每个场景所需要调用的**原子工具矩阵。

---

### 一、 `ai-scenario-ops-sdk` 场景全景与工具映射矩阵

```
                                  ┌─────────────────────────────────────────┐
                                  │   ai-scenario-ops-sdk (运维场景套件)   │
                                  └────────────────────┬────────────────────┘
                                                       │
         ┌───────────────────┬───────────────────┼───────────────────┬───────────────────┐
         ▼                   ▼                   ▼                   ▼                   ▼
┌───────────────────┐┌───────────────────┐┌───────────────────┐┌───────────────────┐┌───────────────────┐
│ 1. DBA 专项诊断域 ││ 2. 日志与链路分析域││ 3. 应用与 JVM 诊断域││ 4. 云原生与网络域 ││ 5. 变更与安全防护域│
│   (ops.db)        ││   (ops.log)       ││   (ops.app)       ││   (ops.k8s)       ││   (ops.sec)       │
└───────────────────┘└───────────────────┘└───────────────────┘└───────────────────┘└───────────────────┘

```

#### 1. DBA 专项诊断域 (`ops.db`)

数据库是系统的核心命脉，对稳定性与安全性要求极高（需强依赖 `ai-tool-sdk` 的安全沙箱）。

* **场景 1.1：慢 SQL 自动排查与优化建议 (`SlowSqlAnalyzer`)**
* **场景逻辑**：捕获慢 SQL，结合表结构、执行计划与索引状态，给出优化建议与安全索引建设计划。
* **涉及工具**：`SlowLogFetcherTool`（拉慢日志）、`TableSchemaTool`（查表结构）、`SqlExplainTool`（查执行计划）、`IndexAdvisorTool`（分析基数与索引）。


* **场景 1.2：数据库死锁与锁等待排查 (`LockConflictAnalyzer`)**
* **场景逻辑**：出现 Lock Wait Timeout 或 Deadlock 时，分析持锁事务与等待事务，找出阻塞元凶。
* **涉及工具**：`EngineStatusFetcherTool`（`SHOW ENGINE INNODB STATUS`）、`TransactionInspectorTool`（未提交长事务列表）。


* **场景 1.3：连接池爆满与磁盘容量预警 (`DbCapacityAnalyzer`)**
* **场景逻辑**：连接池告警或磁盘容量不足时，定位哪些客户端占用连接过多、哪些表增长异常。
* **涉及工具**：`ConnectionStatTool`（按 IP/客户端统计连接）、`TableSizeInspectorTool`（查 Top 磁盘占用表与碎片）。



#### 2. 日志与链路分析域 (`ops.log`)

应对海量日志、分布式链路追踪与突发异常。

* **场景 2.1：日志性能瓶颈分析 (`LogPerformanceAnalyzer`)** *(你提到的场景)*
* **场景逻辑**：解析耗时超过阈值的 API/RPC 日志，归纳高频卡顿点（如第三方 API 响应慢、序列化耗时、IO 阻塞）。
* **涉及工具**：`LogParserTool`（按正则/Grok 解析日志）、`EsQueryTool` / `LokiQueryTool`（日志过滤与 P95/P99 聚合）、`LogPatternClusterTool`（海量 Error 日志自动聚类）。


* **场景 2.2：分布式链路故障定位 (`TraceTroubleshooter`)**
* **场景逻辑**：微服务调用报错（如 500/504），沿着 TraceId 自动定位哪一个上游/下游服务抛出了源头异常。
* **涉及工具**：`TraceQueryTool`（SkyWalking / Jaeger 链路树获取）、`LogParserTool`（精确提取故障节点的 Error Stack Trace）。



#### 3. 应用与 JVM 诊断域 (`ops.app`)

解决应用层面的 CPU 100%、内存泄漏、GC 停顿等问题。

* **场景 3.1：JVM 内存泄漏与 OOM 分析 (`JvmHeapAnalyzer`)**
* **场景逻辑**：应用发生 OOM 或频繁 FullGC 时，分析 Thread Dump 或 Heap 诊断摘要，定位大对象与泄露根源。
* **涉及工具**：`JcmdTool` / `JmapTool`（获取 GC 统计）、`MatReportReaderTool`（读取 MAT 分析报告中的 Top 大对象类）。


* **场景 3.2：应用 CPU 飙高与线程死锁分析 (`CpuThreadAnalyzer`)**
* **场景逻辑**：容器/虚拟机 CPU 占用 100%，找出消耗 CPU 最高的 Java 线程及对应方法栈。
* **涉及工具**：`ThreadDumpTool`（线程快照）、`ArthasProfileTool` / `AsyncProfilerTool`（采集中断调用栈或生成 FlameGraph 火焰图摘要）。



#### 4. 云原生与网络域 (`ops.k8s`)

应对容器漂移、Pod 频繁重启、网络不通等云原生运维难题。

* **场景 4.1：Pod 频繁重启/CrashLoopBackOff 诊断 (`PodCrashAnalyzer`)**
* **场景逻辑**：容器崩溃时，自动提取 Pod 事件、上一次退出日志（`previous log`）与资源限制配置。
* **涉及工具**：`K8sEventFetcherTool`（`kubectl describe pod` 事件）、`K8sLogFetcherTool`（Crash 前最后日志）、`K8sResourceInspectorTool`（检查 OOMKilled 标志与 Limits）。


* **场景 4.2：网络连通性与 Service 路由排查 (`NetworkTroubleshooter`)**
* **场景逻辑**：服务间调用超时或 DNS 解析失败时，定位是 NetworkPolicy、CoreDNS 还是 Ingress 规则问题。
* **涉及工具**：`K8sServiceInspectorTool`（检查 Service 背后的 Endpoints 是否有效）、`PingTelnetTool`（检测 Pod 间网络端口连通性）。



#### 5. 变更与安全防护域 (`ops.sec`)

防范发布引入故障、误操作与异常流量。

* **场景 5.1：上线变更后异常回滚决策 (`ReleaseHealthChecker`)**
* **场景逻辑**：新版本发布后，自动对比新旧版本的 Error Log 变化率、P99 延迟变动，给出“继续发布”或“触发回滚”的建议。
* **涉及工具**：`PrometheusMetricTool`（拉取灰度节点黄金指标）、`GitCommitFetcherTool`（查看本次发布对应的 Git Diff / Commit 信息）。


* **场景 5.2：安全入侵与异常 SQL/API 行为审查 (`SecurityAnomalyInspector`)**
* **场景逻辑**：检测到高危未授权请求或未走索引的大表全表扫描时，定位来源 IP、用户及关联的 API 路径。
* **涉及工具**：`WafLogTool`（Web 应用防火墙日志）、`AuditLogTool`（数据库审计日志）。



---

### 二、 升级“铁三角”后，`ai-scenario-ops-sdk` 的工程落地架构

在有了 `ai-tool-sdk` 之后，`ai-scenario-ops-sdk` 内部的代码组织非常清晰，它**不需要自己实现具体的工具类**，只需依赖对应的领域工具包（如 `ai-tool-db`、`ai-tool-ops`），或者由宿主应用在运行时注入工具。

#### 1. 内部 SDK 代码结构（Package 隔离）：

```
ai-scenario-ops-sdk
├── common/                               # Ops 场景通用提示词与出参基类
│   └── BaseOpsAnalysisResult.java        # 包含：faultCause, severity, actionItems
├── db/                                   # DBA 运维场景子包
│   ├── SlowSqlScenarioExecutor.java      # 慢 SQL 自动排查与优化
│   ├── LockConflictScenarioExecutor.java # 死锁排查
│   └── dto/                              # 结构化出参 SlowSqlAnalysisResult
├── log/                                  # 日志运维场景子包
│   ├── LogPerformanceScenarioExecutor.java # 日志性能瓶颈分析
│   └── TraceTroubleshooterExecutor.java    # 分布式链路故障定位
├── k8s/                                  # 云原生运维场景子包
│   └── PodCrashScenarioExecutor.java     # Pod Crash 诊断
└── OpsScenarioAutoConfiguration.java     # Spring Boot 自动装配门面

```

#### 2. 代码实现示例：慢 SQL 场景执行器

看看在新底座下，场景执行器的编写有多干净：

```java
@Component
public class SlowSqlScenarioExecutor {

    private final AgentRunner agentRunner; // 来自 ai-agent-sdk
    
    // 注入实现 ai-tool-sdk 契约的原子工具 (来自 ai-tool-db 包)
    private final SlowLogFetcherTool slowLogTool;
    private final SqlExplainTool explainTool;
    private final TableSchemaTool schemaTool;

    public SlowSqlAnalysisResult analyze(SlowSqlRequest request) {
        // 1. 场景按需组装工具箱 (ToolRegistry 来自 ai-tool-sdk)
        ToolRegistry registry = new ToolRegistry();
        registry.register(slowLogTool);
        registry.register(explainTool);
        registry.register(schemaTool);

        // 2. 内置 DBA 运维专家 Prompt
        String systemPrompt = """
            你是一位资深的 MySQL DBA 专家。
            你需要根据用户提供的 Slow Log ID，调用工具获取慢 SQL 内容、表结构和 EXPLAIN 执行计划。
            分析其性能瓶颈（如全表扫描、索引失效、临时表等），并给出：
            1. 优化后的 SQL 语句；
            2. 推荐的新建索引 DDL（必须评估建索引对写操作的影响）；
            3. 风险等级与预估提升倍数。
            """;

        // 3. 驱动 ReAct 循环并强类型提取结构化报告
        return agentRunner.runAndExtractObject(
            systemPrompt,
            "请分析 SQL ID 为 [" + request.getSqlId() + "] 的慢查询",
            registry,
            SlowSqlAnalysisResult.class // 提纯后的强类型 Java Record/DTO
        );
    }
}

```

---

### 三、 底层“铁三角”为场景层提供的保障

在这个设计中，上层场景能够极其从容地应对复杂的运维环境，是因为底层“铁三角”承担了所有脏活累活：

1. **安全保障（`ai-tool-sdk` 承担）**：
当 Agent 在慢 SQL 分析或 DBA 诊断场景中发起 SQL 查询时，`ai-tool-sdk` 内置的 **安全沙箱（SafeGuard）** 会自动拦截任何非 `SELECT/EXPLAIN` 的写操作（防 `DROP/DELETE` 误删），场景 SDK 无需重复编写安全防御逻辑。
2. **防 Token 爆表（`ai-tool-sdk` + `ai-agent-sdk` 联合承担）**：
当日志分析工具（`LogParserTool`）或者 SQL 诊断工具查出上万行日志/数据时，`ai-tool-sdk` 内部的 `ToolResultTruncator` 会自动截断中间冗余，保留头尾关键信息，保护 Prompt 不会爆掉。
3. **高可用与厂商容错（`ai-client-sdk` 承担）**：
在紧急故障排查时，如果某个大模型供应商触发了 Rate Limit（429）或服务崩溃，`ai-client-sdk` 的 Key 轮询与抖动重试机制保证了运维诊断流程不会中断。

### 总结

`ai-scenario-ops-sdk` 作为 **AI Workbench 上层的“第一战术套件”**，通过 **`db` / `log` / `app` / `k8s` / `sec**` 5 大场景域彻底覆盖了日常 SRE 与 DBA 的核心工作流。场景层本身保持高度轻量，专注做“专家经验”，把底层工具契约与安全交给 `ai-tool-sdk`，把 ReAct 调度交给 `ai-agent-sdk`，实现了完美的职责隔离。