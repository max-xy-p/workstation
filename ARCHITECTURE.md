# Architecture

## 总览

Workstation 是前后端分离的单用户工作台。浏览器只访问 Web/Nginx 入口；身份认证由 Keycloak 负责，Gateway 作为 OAuth2 Client 与 BFF 保存浏览器会话并向后端转发 Access Token；Backend 是无状态 OAuth2 Resource Server。

```text
Browser
  -> Web/Nginx
      -> Vue 静态资源
      -> /auth/**                         -> Keycloak -> MySQL(keycloak)
      -> /oauth2/**、/login/oauth2/**     -> Gateway
      -> /api/**                          -> Gateway -> Backend -> MySQL
                                                   -> Redis（Gateway Session）
```

浏览器不保存 Access Token 或 Refresh Token，只持有 HttpOnly 的 `WORKSTATION_SESSION` Cookie。所有业务 API 仍位于 `/api`。

Python 客户端使用独立的 Keycloak Client Credentials，经同一 Gateway 入口携带 Bearer Token 调用业务 API，不使用浏览器 Session 或 CSRF：

```text
Python -> Keycloak Token Endpoint -> Bearer Access Token
       -> Gateway Resource Server -> Backend Resource Server -> MySQL
```

## 认证与授权边界

- Keycloak：账号、密码、登录页面、SSO 会话、OIDC Token、暴力破解保护和 `OWNER` 角色。
- Gateway：Authorization Code + PKCE、OIDC 回调、Redis Session、CSRF、当前用户接口、注销和 Token Relay。
- Backend：只验证 Bearer JWT 的签名、Issuer、Audience、有效期和 `OWNER` 角色，不再实现登录、Session 或浏览器 CSRF。
- Vue：触发登录跳转、读取 `/api/auth/me`、获取 CSRF Token；不接触 OAuth2 Token。
- Python Client：使用 Client Credentials 获取短期 Token，通过 Gateway 调用 API；不保存用户密码。

```text
登录：LoginView -> /oauth2/authorization/keycloak -> Keycloak
    -> /login/oauth2/code/keycloak -> Gateway Session -> Vue

读请求：Browser Cookie -> Gateway -> Bearer Access Token -> Backend
写请求：Browser Cookie + CSRF Header -> Gateway -> Bearer Access Token -> Backend
机器请求：Python Bearer Token -> Gateway 校验并保留 Token -> Backend 再次校验
退出：POST /api/auth/logout + CSRF -> 清除 Gateway Session -> Keycloak Logout
```

Gateway 中的认证状态用于浏览器会话，Backend 中保留的是资源访问校验，不是重复实现登录。即使内部网络绕过 Gateway，Backend 仍会拒绝无效 Token 或缺少 `OWNER` 角色的请求。

## 业务模块

- Notes：Markdown 笔记 CRUD、多级文件夹、搜索、标签/项目筛选和文件导入。
- Projects：项目资料、状态以及关联任务和笔记。
- Tasks：任务 CRUD、优先级、截止日期和看板状态。
- Dashboard：活跃项目、任务数量和最近记录。
- Frontend shell：概览、项目、任务和标签使用全局侧栏；笔记使用独立工作区外壳。

后端业务沿用 `Controller -> Service -> Mapper -> MySQL` 分层。写操作和关联对象校验位于 Service。

## 笔记数据流

```text
NotesView / NoteEditorView
  -> frontend/src/api/workstation.ts
  -> clients/typescript (@workstation/api-client)
  -> Gateway Token Relay
  -> NoteController / NoteFolderController
  -> NoteService / NoteFolderService
  -> MyBatis Mapper
  -> notes / note_folders / note_tag / tags
```

笔记正文以 Markdown 保存在 MySQL。浏览器 IndexedDB 的 `workstation-cache/note-drafts` 仅保存尚未获得服务端确认的草稿。更新成功后 `notes.version` 递增，前端确认响应版本后才清理草稿。

## 关键组件

- `gateway/SecurityConfig`：OIDC、PKCE、CSRF、角色映射、登录回调和全局退出。
- `gateway/AuthController`：`/api/auth/csrf` 与 `/api/auth/me`。
- `backend/config/SecurityConfig`：无状态 JWT Resource Server 和 `OWNER` 授权。
- `keycloak/workstation-realm.json`：Realm、Client、Audience Mapper、角色和初始 Owner。
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

- MySQL 8：使用独立的 `workstation` 与 `keycloak` 数据库分别保存业务和身份数据。
- Redis 8：Gateway 服务端 Session。
- 本机文件系统：受 `NOTE_IMPORT_ALLOWED_ROOTS` 限制的 Markdown 导入源。
- Nginx：唯一公开入口和同源反向代理。

CI 验证 Backend、Gateway、Frontend 和三个应用镜像；发布流程把 Backend、Gateway、Web 镜像推送到 GHCR，并在 VPS 上通过生产 Compose 运行它们以及 MySQL、Redis、Keycloak。
