# 系统架构

## 总览

Workstation 是前后端分离、以 Workspace 为资源边界的多人知识与工作台。浏览器只访问 Web/Nginx 入口；身份认证由 Keycloak 负责，Gateway 作为 OAuth2 Client 与 BFF 保存浏览器会话并向后端转发 Access Token；Backend 是无状态 OAuth2 Resource Server，并负责 Workspace 成员授权和审计。

```text
Browser
  -> Web/Nginx
      -> Vue 静态资源
      -> /auth/**                         -> Keycloak -> MySQL(keycloak)
      -> /oauth2/**、/login/oauth2/**     -> Gateway
      -> /api/**                          -> Gateway -> Backend -> MySQL
                                                   -> Redis（Gateway Session / Backend 出站限流）
                                                   -> audit_outbox
                                                        -> XXL-JOB -> RabbitMQ
                                                        -> Audit Consumer -> audit_events
```

浏览器不保存 Access Token 或 Refresh Token，只持有 HttpOnly 的 `WORKSTATION_SESSION` Cookie。所有业务 API 仍位于 `/api`。

Python 客户端使用独立的 Keycloak Client Credentials，经同一 Gateway 入口携带 Bearer Token 调用业务 API，不使用浏览器 Session 或 CSRF：

```text
Python -> Keycloak Token Endpoint -> Bearer Access Token
       -> Gateway Resource Server -> Backend Resource Server -> MySQL
```

## 认证与授权边界

- Keycloak：账号、密码、登录页面、SSO 会话、OIDC Token、暴力破解保护，以及 `WORKSTATION_USER`、`WORKSTATION_ADMIN`、`WORKSTATION_SERVICE` 系统角色。
- Gateway：Authorization Code + PKCE、OIDC 回调、Redis Session、CSRF、当前用户接口、注销、Token Relay 和系统角色入口校验；不读取 Workspace 成员关系。
- Backend：验证 Bearer JWT 的签名、Issuer、Audience、有效期和系统角色，并依据 Workspace 成员角色执行 Permission 授权；关键写操作在业务事务中产生审计 Outbox，权限拒绝尽力独立记录；不实现登录、Session 或浏览器 CSRF。
- Vue：触发登录跳转、读取 `/api/auth/me`、获取 CSRF Token、选择当前 Workspace；不接触 OAuth2 Token，也不作为业务授权的可信边界。
- Python Client：使用 Client Credentials 获取短期 Token，通过 Gateway 调用明确授权的 Workspace API；不保存用户密码。

```text
登录：LoginView -> /oauth2/authorization/keycloak -> Keycloak
    -> /login/oauth2/code/keycloak -> Gateway Session -> Vue

读请求：Browser Cookie -> Gateway -> Bearer Access Token -> Backend
写请求：Browser Cookie + CSRF Header -> Gateway -> Bearer Access Token -> Backend
机器请求：Python Bearer Token -> Gateway 校验并保留 Token -> Backend 再次校验
退出：POST /api/auth/logout + CSRF -> 清除 Gateway Session -> Keycloak Logout
```

Gateway 中的认证状态用于浏览器会话和系统级准入，Backend 中保留的是资源访问校验，不是重复实现登录。即使内部网络绕过 Gateway，Backend 仍会拒绝无效 Token、缺少系统角色或没有目标 Workspace 权限的请求。

认证设计、两类调用方的区别及完整流程见 [`docs/authentication.md`](docs/authentication.md)。

## 业务模块

- Workspaces：个人工作空间幂等初始化、共享空间创建、成员管理、角色权限和审计查询。
- Notes：Markdown 笔记 CRUD、版本条件更新、内容快照、历史差异与恢复、软删除与恢复、多级文件夹、搜索、标签/项目筛选和文件导入。
- Projects：项目资料、状态、项目入口以及关联任务、笔记和多个外部仓库。
- Integrations：Workspace 内的 GitHub/GitLab Token 连接、仓库发现导入、持久化任务队列、手动和定时同步，以及近期 Issue、PR/MR、默认分支提交和流水线快照；流程见 [`docs/integrations.md`](docs/integrations.md)。
- Tasks：任务 CRUD、依据笔记、优先级、截止日期和看板状态，以及 GitHub Issue 创建任务、已有任务来源关联和状态差异提示。
- Dashboard：活跃项目、任务数量和最近记录。
- Frontend shell：所有业务模块共用导航、Workspace 切换与页面操作区；笔记在统一外壳内提供文件树和编辑区，可主动进入专注模式。数据源分为连接列表和连接详情；项目、任务、标签、文件夹与连接表单按需打开抽屉，浏览页不常驻编辑表单。

笔记、文件夹、标签、项目和任务都归属于 Workspace。所有业务路径显式携带 `/api/workspaces/{workspaceId}`，后端业务沿用 `Controller -> Service -> Mapper -> MySQL` 分层；Service 先执行集中授权，再校验同 Workspace 关联对象和事务规则，Mapper 的查询与写入始终携带 `workspaceId`。

## 笔记数据流

```text
NotesView / NoteEditorView
  -> /w/{workspaceId}/notes
  -> frontend/src/api/workstation.ts
  -> clients/typescript (@workstation/api-client)
  -> Gateway Token Relay
  -> NoteController / NoteFolderController
  -> NoteService / NoteFolderService
  -> MyBatis Mapper
  -> WorkspaceAuthorizationService
  -> notes / note_revisions / note_folders / note_tag / tags（workspace_id）
```

笔记正文以 Markdown 保存在 MySQL。浏览器 IndexedDB 的 `workstation-cache/note-drafts` 仅保存尚未获得服务端确认的草稿，草稿键包含用户主体、Workspace、笔记标识和编辑会话。更新请求携带读取时的版本，Backend 条件更新成功后递增 `notes.version`；不匹配时返回 `409`，Frontend 保留草稿并展示服务端版本，只有确认响应版本后才清理已确认的草稿。删除操作写入 `deleted_at` 和删除主体并隐藏笔记，恢复时生成新版本；只有回收站的永久删除操作才移除正文和标签关联。

`note_revisions` 在笔记事务内保存不可变标题与正文快照，普通连续相同内容去重，显式历史恢复记录来源并生成新版本。恢复只替换标题和正文，保留当前关联；历史与笔记按 Workspace 隔离，永久删除笔记时级联清除。Frontend 按需加载历史抽屉及差异 Worker，恢复前保留未确认草稿并在成功后更换编辑会话。共享历史、展示阈值及维护窗口见 [笔记历史](docs/note-history.md)。

版本冲突由 Frontend 的三方合并面板比较已有草稿基准、本地修改和最新服务端笔记。独立行修改自动合并，标题及关联属性冲突需明确选择。原草稿和合并结果分会话保留，结果先写入 IndexedDB 并暂停自动保存，用户确认后仍通过原更新接口及 `expectedVersion` 条件写入；再次冲突继续比较，不在客户端绕过服务端规则。缺少基准、大文本及 Worker 故障降级为手动核对。

## 关键组件

- `gateway/SecurityConfig`：OIDC、PKCE、CSRF、角色映射、登录回调和全局退出。
- `gateway/AuthController`：`/api/auth/csrf` 与 `/api/auth/me`。
- `backend/config/SecurityConfig`：无状态 JWT Resource Server 和 `WORKSTATION_*` 系统角色准入。
- `backend/security/CurrentPrincipalResolver`：集中解析可信 JWT 主体，不让业务 Controller 直接读取 Claim。
- `WorkspaceAuthorizationService`：Workspace 成员关系、角色到 Permission 的统一授权边界。
- `AuditService`：捕获关键写操作与 Workspace 权限拒绝事件，成功事件与业务事务共同写入 Outbox，拒绝事件失败不改变授权响应。
- `AuditOutboxRelayJob`：由 XXL-JOB 触发，使用短租约领取 Outbox 并通过 Publisher Confirm 可靠发布。
- `AuditMessageConsumer`：消费 RabbitMQ 消息并按 `eventId` 幂等写入最终审计表，耗尽重试后进入死信队列。
- `keycloak/workstation-realm.json`：Realm、Client、Audience Mapper、系统角色和初始用户。
- `frontend/src/workspace.js`：当前 Workspace 列表、选择和页面上下文。
- `frontend/src/utils/pageLayout.mjs`：模块导航高亮与页面实例边界；同一 Workspace 切换笔记保留文件树，切换 Workspace 重建模块上下文。
- `AppDrawer.vue` 与 `useFormPanel.mjs`：按需编辑、键盘与焦点、关闭时的未保存提醒、提交锁定和表单内错误。
- `frontend/src/api/client.ts`：同源 API、Cookie、CSRF、未认证事件及类型化底层请求。
- `frontend/src/api/workstation.ts`：向独立 TypeScript SDK 注入浏览器 Transport，并维护 Gateway BFF 专用接口。
- `clients/typescript`：独立的 `@workstation/api-client` SDK，包含 OpenAPI DTO、业务 endpoint、分页汇总和可注入 Token 的 Fetch Transport。
- `clients/typescript/src/generated/schema.ts`：由 `openapi/workstation.yaml` 生成，禁止手工修改。
- `MarkdownEditor.vue`：Tiptap 与 Markdown 转换。
- `noteDraftStore.js`：IndexedDB 待同步草稿。
- `GlobalExceptionHandler`：业务异常的统一 JSON 响应。
- `openapi/workstation.yaml`：Backend、Python 与 Vue 共用的 Request、Response、分页、错误和认证 HTTP 契约真源。
- `clients/python/workstation_client`：由 OpenAPI 生成的 Python 客户端。
- `clients/python-auth/workstation_auth`：Client Credentials Token 获取和缓存薄层。

## 任务的外部来源

`external_issue_sources` 保存已关联 GitHub Issue 的稳定身份与来源快照，`task_issue_links` 保存 Workspace 内一对一关系和关联代次。近期开发快照可重建，任务不引用其临时 ID。Backend 在同一事务创建任务、关系和审计；来源同步只写外部快照，本地任务继续由用户维护。

定点跟踪复用数据源队列、连接租约及执行版本，运行表保存冻结上界和逐项游标。提交前核对关联代次、仓库绑定和当前连接，防止旧响应覆盖解除或接管后的关系。来源读取通过 SDK 的独立接口提供，普通任务旧请求不会清除来源。操作、共享与升级边界见 [数据源流程](docs/integrations.md#github-issue-与本地任务)。

任务来源关联另保存确认基准，最新来源与基准的业务差异推导待处理。确认由任务编辑者显式发起，核对关联代次、来源版本、确认版本及任务版本；可选任务状态修改、基准推进与审计同事务提交。Tracker 不自动确认，读取失败不消除差异；该能力是共享任务决策，不是个人消息收件箱。

## Webhook 通知与指定刷新

GitHub 经 Web/Nginx、Gateway 专用路由进入 Backend，使用原始字节签名认证，不使用 Session 或 Bearer。投递收件箱保存去重标识、原文哈希、有效来源及关联代次，提交后才确认接收。通知按来源合并为原有同步队列的 ISSUE_REFRESH 作业，目标逐项持久化，复用 Tracker 的平台读取和安全提交。

批量跟踪保留上界与游标，指定刷新记录目标完成状态；两者分别维护进度，共用来源身份、权限、租约和关联校验。通知只是刷新提示，不直接应用事件内容。定时整批校准继续处理遗漏，并优先于持续到来的通知；指定刷新不延后整批校准时间。安全、重试及升级见 [数据源流程](docs/integrations.md#github-webhook-与指定来源刷新)。

## 外部 API 调用协调

RemoteApi 在每次真实网络尝试前，通过 ExternalApiLimiter 的 Lua 脚本申请 Redis 共享额度；Key 按平台、实例和凭证摘要隔离。平台限流响应发布只延长的共享冷却；协调不可用时暂停出站请求。后台抛出延期信号，由 IntegrationWorker 调用短事务将运行重新排队，清空租约并保存下次执行时间；不会占用线程等待冷却或把临时受限记录成来源读取失败。

integration_sync_runs 的内部进度保存发现页码和有界采集中间结果；仓库、类别与分页检查点随相关结果事务提交，避免小额度下不断重读开头。Issue 跟踪与指定刷新继续使用已有游标和逐项目标。Redis 丢失不丢任务，旧租约和变更配置不能继续提交。部署、交互式验证与额度作用域见 [数据源流程](docs/integrations.md#外部-api-共享限流与冷却)。

## 外部依赖

- MySQL 8：使用独立的 `workstation`、`keycloak` 与 `xxl_job` 数据库分别保存业务、身份和调度数据。
- Redis 8：Gateway 服务端 Session，以及 Backend 外部 API 共享令牌桶与限流冷却；任务和恢复进度仍在 MySQL。
- RabbitMQ 4：审计消息持久化传输、消费重试和死信隔离。
- XXL-JOB 3：调度审计 Outbox Relay，提供任务日志、告警和手工补偿入口。
- 本机文件系统：受 `NOTE_IMPORT_ALLOWED_ROOTS` 限制的 Markdown 导入源。
- Nginx：唯一公开入口和同源反向代理。

CI、GHCR 镜像发布和 VPS 部署流程见 [`docs/ci-cd.md`](docs/ci-cd.md)，本文不重复维护操作步骤。
审计事务边界、消息投递和故障处理见 [`docs/audit/README.md`](docs/audit/README.md)。

## 任务并发写入

任务编辑、状态切换和删除以用户读取的版本作条件更新，数据库原子判断决定是否提交；成功更新递增版本，失败返回任务冲突或资源不存在，成功审计与修改同事务。来源确认沿用相同状态修改入口，依据笔记清除使旧任务版本失效。前端保留冲突输入并展示最新内容，仅用户显式确认后重提，不自动合并或覆盖。客户端升级边界见 [任务编辑并发保护](docs/task-concurrency.md)。
