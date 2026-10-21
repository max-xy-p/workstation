# OpenAPI 契约说明

## 为什么引入 OpenAPI

Workstation 同时存在 Java Backend、Python Client、TypeScript SDK 和 Vue Frontend。如果每种语言各自维护 Request、Response、分页和错误类型，同一个字段很容易出现名称、可空性或时间格式不一致。

OpenAPI 的作用是把调用方可见的 HTTP 约定集中到一处，让不同语言根据同一份契约生成自己的原生类型。共享的是契约，不是某一种语言的 DTO 文件。

## 契约真源的含义

`openapi/workstation.yaml` 是 HTTP API 的契约真源，负责：

- 路径、方法、参数和状态码
- Request、Response、分页和错误结构
- 字段必填、可选、可空、枚举和格式
- 机器调用使用的 OAuth2 Client Credentials 声明

“契约真源”只针对 HTTP 边界，不代表 OpenAPI 接管全部业务模型。数据库表、事务、文件夹层级、删除限制、关联对象校验、笔记版本递增和草稿同步仍由 Backend 与 Frontend 实现。

例如，契约可以规定更新后的笔记响应必须包含版本号，但版本如何递增、何时提交事务，仍属于 Backend 业务规则。

`3.0.0` 契约要求笔记更新提交 `expectedVersion`。服务端仅在版本匹配时写入，版本冲突返回 `409` 和 `NOTE_VERSION_CONFLICT`。`3.1.0` 增加回收站列表、恢复和永久删除接口；普通删除改为软删除，但原有路径和成功状态码保持不变。成员添加请求只接收主体 ID 与角色；主体资料由服务端已知身份记录决定。任务可关联同项目的依据笔记，笔记搜索摘要提供短正文片段。

`4.0.0` 增加 Workspace 数据源连接、仓库发现导入与开发快照接口，以及项目入口 `homepageUrl`。项目完整更新必须明确提交该字段，`null` 表示清除；不保留旧客户端缺少字段时的兼容行为，调用方需同步升级。数据源流程见 [`integrations.md`](integrations.md)。

`4.1.0` 将成员候选查询扩展为用户名或显示名称的包含匹配，不区分大小写；最多返回 10 个用户。路径、必填参数 `username`、返回字段和权限保持原有契约，完整用户名查询仍有效，客户端可按原有方式调用。

`4.2.0` 兼容新增笔记历史摘要分页、快照详情和正文恢复接口。分页可通过 `upToVersion` 固定上界；恢复必填 `expectedVersion`，成功返回新笔记详情，冲突仍为 `409 / NOTE_VERSION_CONFLICT`。原保存请求和回收站恢复接口保持不变。历史由服务端自动记录，客户端不提交作者或来源；权限、草稿及迁移边界见 [笔记历史](note-history.md)。各语言 SDK 同步为 `4.2.0`。

`4.3.0` 兼容新增 GitHub Issue 创建任务、关联已有任务、解除来源以及已关联来源分页查询接口。创建返回 `201`，重复创建返回已有任务和 `200`，指定关联冲突返回 `409 / TASK_ISSUE_CONFLICT`。开发记录可选增加 Issue 编号、关闭原因和关联任务 ID。旧任务请求与状态枚举不变，普通保存不清除来源；来源信息通过独立接口读取。各语言 SDK 及认证层同步为 `4.3.0`。

`4.4.0` 兼容新增 GitHub Webhook 配置、投递分页与显式重试接口，以及使用独立签名认证的原文回调。回调不使用 OAuth，合法投递持久化后返回 202；配置使用 expectedVersion 防止并发覆盖。作业 kind 可出现 ISSUE_REFRESH，原有作业和任务接口不变。全套 SDK 及认证层为 `4.4.0`，浏览器 SDK 仅封装管理接口，不替 GitHub 调用签名回调。

## 契约与 SDK 的关系

```text
OpenAPI
  -> Backend 按契约提供 API
  -> Python 生成 DTO 和 endpoint
  -> TypeScript 生成 DTO
  -> TypeScript SDK 封装业务 endpoint
  -> Frontend 注入浏览器 Transport
```

Python 和 TypeScript 最终得到不同语言的代码，但字段和接口来源一致。认证适配位于生成目录之外，因为同一套业务接口可以由浏览器 Session、机器 Bearer Token 或未来其他认证方式调用。

## OpenAPI 不解决什么

- 不生成或替代 Service、Mapper 和数据库 Entity。
- 不决定 Keycloak、Gateway、CSRF 或 Session 的实际配置。
- 不保证业务规则正确，仍需要 Service 和集成测试。
- 不描述页面状态、Tiptap 编辑状态、文件夹树节点或 IndexedDB 草稿。
- 不负责把 SDK 自动发送给某个服务；消费服务需要明确声明依赖。

## Schema 设计原则

- 数据库 Entity、Request 和 Response 分离。
- 列表使用摘要模型，详情返回完整内容。
- 分页和错误结构在业务模块间保持一致。
- 时间使用带偏移量的 RFC 3339 表达，避免不明确的本地时间。
- `operationId` 保持唯一和稳定，因为它会影响生成后的 API 名称。
- 对可选和可空作明确区分，避免客户端无法判断“未提供”和“明确为空”。

## 契约变更流程

1. 判断变化是否会被 HTTP 调用方观察到。
2. 调用方可见的变化先修改 OpenAPI，并评估兼容性和版本影响。
3. Backend 同步实现契约，业务规则仍放在正确的 Service 中。
4. 重新生成 Python Client 和 TypeScript DTO。
5. 更新生成目录之外的 SDK 封装或认证适配。
6. 运行相关测试，并确认重复生成没有漂移。
7. 将契约、实现、生成结果和测试作为同一个变更提交。

不得手工修改生成文件来掩盖契约问题。生成结果异常时，应修正契约、生成器配置或生成流程。

## 兼容性与版本

删除 endpoint 或字段、改变 JSON 类型、收紧必填或可空条件、移除枚举值、改变成功状态码，通常都会影响现有客户端。此类变化需要明确判断是否升级主版本。

新增可选字段或新增 endpoint 通常可以保持向后兼容，但仍要验证生成器和现有调用方。

从 `2.0.0` 到 `3.0.0` 的笔记更新请求是破坏性变化。旧 SDK 不会发送 `expectedVersion`，调用方应升级到同版本 SDK 并先读取笔记版本，再提交更新。成员添加请求中的客户端身份资料也已移除。

OpenAPI、Python SDK、Python 认证层和 TypeScript SDK 使用相同语义化版本。发布标签必须与包内版本一致，避免同一个版本名称对应不同契约。

## 为什么提交生成代码

生成结果提交仓库，可以让 Frontend 和测试直接使用确定的版本，也能在代码审查中看到契约对各语言造成的影响。代价是必须通过生成漂移检查，确保提交内容确实来自固定版本的生成器。

SDK 的远程发布和消费方式见 [`sdk-distribution.md`](sdk-distribution.md)，完整 CI 关系见 [`ci-cd.md`](ci-cd.md)。当前覆盖范围和已知问题见 [`STATUS.md`](STATUS.md)。
