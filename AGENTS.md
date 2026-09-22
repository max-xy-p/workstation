# AGENTS.md

## 项目与数据真源

Workstation 是以 Workspace 为资源边界的多人知识与工作台，包含多级文件夹 Markdown 笔记、项目资料和任务看板。概览页是默认入口，笔记使用独立工作区。

Backend 的 MySQL 数据是持久化真源；浏览器 IndexedDB 只保存尚未获得服务端确认的草稿。

## 如何使用本文件

本文件只保留所有任务都适用的约束。开始任务时先读本文件，再按任务涉及的领域读取下表中的指南；跨领域任务应读取所有相关指南。指南位于 `agent-guides/`，只维护 Agent 的开发规范、实现限制和验证要求。面向人的流程、边界与进度仍在 `docs/`。

| 任务涉及 | 按需阅读 |
| --- | --- |
| 技术栈、仓库目录与模块定位 | [项目地图](agent-guides/project-map.md) |
| Backend、数据库、服务端业务规则 | [Backend 规范](agent-guides/backend.md) |
| OpenAPI、Python/TypeScript SDK、接口契约 | [OpenAPI 与 SDK 规范](agent-guides/api-sdk.md) |
| 认证、授权、Token、CSRF、安全边界 | [认证与安全规范](agent-guides/security.md) |
| Frontend、笔记、编辑器、IndexedDB | [Frontend 与笔记规范](agent-guides/frontend-notes.md) |
| Docker、Compose、CI、部署、发布 | [交付规范](agent-guides/delivery.md) |
| 修改 SonarQube 支持的手写代码、查询静态分析结果 | [SonarQube 规范](agent-guides/sonarqube.md) |
| 代码任务的执行顺序、结果汇报 | [Agent 工作流程](agent-guides/workflow.md) |
| 需要运行模块验证 | [验证命令](agent-guides/verification.md) |

任何代码任务都应阅读 [Agent 工作流程](agent-guides/workflow.md)。修改 SonarQube 支持的手写代码时，还应阅读 [SonarQube 规范](agent-guides/sonarqube.md) 和受影响模块的指南。只读调查或文档整理无需运行代码验证。

## 相关项目文档

- 本地运行入口：[README.md](README.md)
- 系统边界和数据流：[ARCHITECTURE.md](ARCHITECTURE.md)
- 文档职责：[docs/README.md](docs/README.md)
- 当前进度和已知问题：[docs/STATUS.md](docs/STATUS.md)
- 认证与授权说明：[docs/authentication.md](docs/authentication.md)
- OpenAPI 变更流程：[docs/openapi.md](docs/openapi.md)
- SDK 分发流程：[docs/sdk-distribution.md](docs/sdk-distribution.md)
- CI/CD 和部署流程：[docs/ci-cd.md](docs/ci-cd.md)

开始开发前阅读与任务相关的文档和源码。文档与代码冲突时，以当前代码为准，并在同一变更中修正文档。

## 通用工作规则

- 不修改或回退与当前任务无关的未提交变更。
- 修改前先确认职责层、数据真源和现有实现，不在调用方重复实现服务端已经存在的规则。
- 优先沿用已有架构、抽象、命名方式和目录结构，不为局部需求建立第二套实现体系。
- 行为变化必须补充与风险相称的测试，并运行受影响模块的验证。
- 新增配置时同步示例配置、Compose、测试和部署流程；不得提交真实 Secret。
- 不建立第二套 DTO、认证状态、草稿存储或业务枚举。
- 不通过手工修改生成文件、关闭检查、添加 `NOSONAR`、降低规则严重级别、修改 Quality Gate 或放宽安全规则来绕过失败。
- 不因为顺手重构而扩大任务范围；与当前需求无关的问题应记录，而不是混入当前变更。
- 修改公共契约、认证模型、持久化结构或部署方式前，应先确认其对其他模块和已发布客户端的影响。
- 完成任务时只声明实际执行并通过的验证，不声称运行了不存在、未配置或未执行的测试、lint、format 或分析。

## 文档与文字规范

- `docs/` 只维护面向人的流程、边界和进度，不复制代码、完整配置、工作流 YAML 或大段命令。
- Agent 开发规范、实现限制和验证要求只维护在本文件与 `agent-guides/`；主题细则放入对应指南，本文件只保留通用约束和入口。
- 当前进度、验证结果和已知问题只维护在 `docs/STATUS.md`。
- 已完成且不再维护的方案文档直接删除，不保留兼容副本。
- 项目维护的文档、手写代码注释、文档字符串和面向使用者的错误消息使用中文。
- 类名、函数名、协议字段和第三方专有名词按代码约定保留。
- OpenAPI 生成目录中的固定模板文本不手工翻译，以免重新生成时丢失或产生漂移。
- 文档只描述当前真实行为，不保留已经废弃实现的描述作为默认方案。
- 如果当前变更使已有文档失效，应在同一次变更中更新对应文档。
