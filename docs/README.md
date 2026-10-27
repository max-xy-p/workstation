# Workstation 文档导航

`docs/` 面向项目维护者和使用者，只说明流程、边界和当前进度，不记录可从代码直接读出的实现细节。

## 文档职责

| 文档 | 内容 |
| --- | --- |
| [`STATUS.md`](STATUS.md) | 已完成事项、进行中事项、后续计划、已知问题和验证记录 |
| [`task-concurrency.md`](task-concurrency.md) | 任务版本校验、冲突处理及客户端升级边界 |
| [`integrations.md`](integrations.md) | GitHub/GitLab 连接、仓库导入、开发快照和同步故障恢复 |
| [`note-history.md`](note-history.md) | 笔记内容历史、差异查看、草稿保护、共享保留边界及升级回退 |
| [`authentication.md`](authentication.md) | 认证设计、组件职责、浏览器与机器调用方式及其流程 |
| [`audit/README.md`](audit/README.md) | 审计边界、Outbox、XXL-JOB、RabbitMQ、幂等与故障处理 |
| [`openapi.md`](openapi.md) | 契约真源的含义、职责边界、Schema 原则和变更流程 |
| [`sdk-distribution.md`](sdk-distribution.md) | SDK、认证层和镜像的区别，以及版本发布和消费方式 |
| [`ci-cd.md`](ci-cd.md) | CI/CD 的作用、交付物、环境边界、发布和回滚流程 |
项目数据源连接与同步流程见 [`integrations.md`](integrations.md)，覆盖 GitHub、GitLab、账号范围、仓库导入和故障恢复。多用户 Workspace 的稳定边界见根目录 [`../ARCHITECTURE.md`](../ARCHITECTURE.md) 和 [`authentication.md`](authentication.md)，审计异步投递流程见 [`audit/README.md`](audit/README.md)，完成状态与迁移注意事项见 [`STATUS.md`](STATUS.md)。进行中的方案保存在 `plans/`，完成后按维护原则清理。

仓库根目录的 [`../README.md`](../README.md) 负责本地运行入口，[`../ARCHITECTURE.md`](../ARCHITECTURE.md) 负责系统整体结构，[`../AGENTS.md`](../AGENTS.md) 及其链接的 `agent-guides/` 负责开发规范、实现约束和验证要求。

## 维护原则

1. 一个流程只保留一份主文档，其他位置只提供链接。
2. 文档描述为什么、由谁负责、按什么顺序执行，不复制大段代码、工作流 YAML 或配置模板。
3. 具体命令以根 README 和项目配置为准；具体实现以源码为准。
4. Agent 开发约束和注意事项写入 `AGENTS.md` 与其链接的 `agent-guides/`，项目进度只写入 `STATUS.md`。
5. 已完成且不再承担维护职责的方案文档直接删除，不保留兼容副本。
