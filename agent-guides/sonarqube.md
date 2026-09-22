# 代码质量与 SonarQube

SonarQube 是本项目代码质量、安全和可维护性静态分析的权威规则来源，但不能替代编译、测试、OpenAPI 契约检查和业务行为验证。

Coding Agent 可通过 SonarQube MCP 获取项目问题、规则定义、Quality Gate、Security Hotspot、重复代码和其他静态分析信息。

## SonarQube 使用规则

- 修改 SonarQube 支持的手写代码后，应在任务结束前使用 SonarQube MCP 检查本次修改涉及的文件。
- 遇到不熟悉的 Sonar 规则时，优先通过 SonarQube 查询规则定义，不凭模型经验猜测规则含义。
- 新代码不得主动引入新的 SonarQube Issue。
- 优先处理当前修改新引入的 Reliability、Security 和 Maintainability 问题。
- Sonar 检查应聚焦当前任务及其影响范围；除非任务明确要求，不顺手大规模修复历史 Sonar 问题。
- 遇到 Sonar Issue 时优先修改代码消除根因，而不是规避检查。
- 不使用 `NOSONAR`、规则关闭、目录排除、降低严重级别或修改 Quality Gate 来消除当前任务产生的问题。
- 不得自行将 Sonar Issue 标记为 Accepted 或 False Positive。
- 如果认为某条规则确实不适用于当前代码，应说明：
  - Sonar Rule 编号；
  - 触发位置；
  - 当前实现；
  - 判断该规则不适用的原因。
- Security Hotspot 必须结合实际数据流、信任边界、输入来源和调用方式进行分析，不得仅为了清除提示而直接标记为安全。
- 对重复字符串、重复代码等可维护性问题，只有在重复内容具有相同语义时才提取公共常量或公共实现。
- 不为了满足 Sonar 而制造错误抽象、过度封装或不必要的公共工具类。
- 对生成代码产生的 Sonar 问题，不直接修改生成文件；优先修改 OpenAPI、模板、生成器配置或生成源。
- SonarQube MCP 不可用、认证失败或分析无法完成时，不得声称 Sonar 检查已经通过；应继续完成能够执行的编译和测试，并明确说明 Sonar 验证未完成。

## 代码任务完成条件

涉及代码修改的任务结束前，应尽量同时满足：

1. 受影响模块可以正常编译；
2. 与行为变化相对应的测试通过；
3. 与当前修改相关的 SonarQube 问题已经检查；
4. 当前修改没有留下已知的新 SonarQube Issue；
5. 未通过关闭检查、降低规则或修改 Quality Gate 绕过问题；
6. 没有意外修改无关模块、生成文件或未提交用户变更。

SonarQube Token、Organization 凭证及其他认证信息只能通过本机安全配置或环境变量提供，不得写入：

- `AGENTS.md` 或 `agent-guides/`
- `.codex/config.toml`
- 源码
- OpenAPI
- 日志
- 镜像层
- Git 仓库
