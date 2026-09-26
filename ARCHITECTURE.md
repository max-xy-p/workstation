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
                                                   -> Redis（Gateway Session）
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
- Notes：Markdown 笔记 CRUD、版本条件更新、软删除与恢复、多级文件夹、搜索、标签/项目筛选和文件导入。
- Projects：项目资料、状态以及关联任务和笔记。
- Tasks：任务 CRUD、依据笔记、优先级、截止日期和看板状态。
- Dashboard：活跃项目、任务数量和最近记录。
- Frontend shell：概览、项目、任务和标签使用全局侧栏；笔记使用独立工作区外壳。

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
  -> notes / note_folders / note_tag / tags（workspace_id）
```

笔记正文以 Markdown 保存在 MySQL。浏览器 IndexedDB 的 `workstation-cache/note-drafts` 仅保存尚未获得服务端确认的草稿，草稿键包含用户主体、Workspace、笔记标识和编辑会话。更新请求携带读取时的版本，Backend 条件更新成功后递增 `notes.version`；不匹配时返回 `409`，Frontend 保留草稿并展示服务端版本，只有确认响应版本后才清理已确认的草稿。删除操作写入 `deleted_at` 和删除主体并隐藏笔记，恢复时生成新版本；只有回收站的永久删除操作才移除正文和标签关联。

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

## 外部依赖

- MySQL 8：使用独立的 `workstation`、`keycloak` 与 `xxl_job` 数据库分别保存业务、身份和调度数据。
- Redis 8：Gateway 服务端 Session。
- RabbitMQ 4：审计消息持久化传输、消费重试和死信隔离。
- XXL-JOB 3：调度审计 Outbox Relay，提供任务日志、告警和手工补偿入口。
- 本机文件系统：受 `NOTE_IMPORT_ALLOWED_ROOTS` 限制的 Markdown 导入源。
- Nginx：唯一公开入口和同源反向代理。

CI、GHCR 镜像发布和 VPS 部署流程见 [`docs/ci-cd.md`](docs/ci-cd.md)，本文不重复维护操作步骤。
审计事务边界、消息投递和故障处理见 [`docs/audit/README.md`](docs/audit/README.md)。
