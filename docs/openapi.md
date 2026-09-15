# Workstation OpenAPI 使用说明

> 状态：已实施，完整 Keycloak/Gateway 运行链路待环境烟测
>
> 更新日期：2026-09-15

## 1. OpenAPI 在本项目中的定位

OpenAPI 是 Workstation 的 **HTTP API 契约真源**。它统一描述 Java Backend 对外提供什么接口，以及 Vue 和 Python Client 应当如何调用这些接口。不同语言生成各自的原生类型，但 Request/Response 字段只在 `openapi/workstation.yaml` 中维护一份。

OpenAPI 负责：

- HTTP 路径和方法。
- Path、Query、Header 参数。
- JSON Request 和 Response 结构。
- 必填、可选、可空、枚举和长度等结构性约束。
- 分页响应。
- 统一错误响应。
- HTTP 成功及失败状态码。
- OAuth2 Client Credentials 认证要求。
- 可供人阅读的接口说明和示例。

OpenAPI 不负责：

- MySQL 表、索引、外键和数据库迁移。
- Java Entity 和 MyBatis Mapper。
- Service 事务。
- 文件夹层级、循环检测和删除限制。
- 项目、任务、笔记和标签的关联对象存在性校验。
- `notes.version` 的递增实现。
- IndexedDB 草稿保存、恢复及清理时机。
- Keycloak、Gateway 和 Nginx 的实际安全配置。

例如，OpenAPI 定义 `NoteDetailResponse.version` 是必填的 `int64`；每次笔记更新后 `version` 必须递增，仍由 Backend 的 Service、Mapper 和测试保证。

## 2. 使用位置

```text
                         ┌──────────────────────┐
                         │ openapi/workstation │
                         │        .yaml         │
                         └──────────┬───────────┘
                                    │
                ┌───────────────────┼─────────────────────┐
                │                   │                     │
                ▼                   ▼                     ▼
        Backend 契约实现      Python Client 生成      TypeScript SDK
        Controller/DTO        模型和请求函数          DTO/业务 endpoint
                │                   │                     │
                ▼                   ▼                     ▼
          Service/Mapper       Python 应用和脚本       Vue API 适配层
                │                   │                     │
                └──────────► Gateway ◄────────────────────┘
                                    │
                                    ▼
                                  Backend

                  文档、契约校验和生成漂移检查覆盖以上分支
```

### 2.1 Backend

Backend 的 Controller 和 API DTO 实现 OpenAPI 契约：

- Controller 路径、HTTP 方法、参数和状态码必须与契约一致。
- Request/Response DTO 的 JSON 字段必须与 Schema 一致。
- Java 数据库 Entity 不直接作为 API Response。
- Service 继续负责业务校验和事务。
- Mapper 继续负责 MySQL 访问。

OpenAPI 不生成或替换 Service、Mapper 和数据库 Entity。是否生成 Java API 接口或 DTO，可在实施时评估；第一阶段以现有 Java 代码实现契约为主。

### 2.2 Gateway

Gateway 是浏览器客户端和 Python 客户端的统一 API 入口。

OpenAPI 在这里描述允许的认证方式和 Header，但不生成 Gateway 安全配置。Gateway 必须自行实现：

- 浏览器 Session 认证。
- 浏览器写请求的 CSRF 校验。
- 浏览器 Access Token 的 Token Relay。
- Python Bearer Token 的验证或受控透传。
- 两种认证链路之间的安全隔离。

### 2.3 Python Client

Python Client 从 OpenAPI 生成：

- Request/Response 模型。
- 枚举。
- 路径和查询参数。
- 同步或异步 HTTP 调用函数。
- 响应状态和错误解析基础结构。

Token 获取、刷新、Secret 读取、日志脱敏和高层业务操作放在生成代码外，不修改生成文件。

`clients/python` 中的源码、README 和包配置由生成器模板控制，其中可能包含英文固定文本。项目不直接翻译生成文件；中文使用说明统一维护在 `clients/README.md`、`clients/python-auth/README.md` 和本文档中。手写 Python 文件的文档字符串与必要注释使用中文。

### 2.4 Vue Client

独立的 `clients/typescript` 包从同一契约生成 DTO，并提供业务 endpoint、分页汇总和标准 Fetch Transport。它以 `@workstation/api-client` 本地 npm 包的方式供 Vue 使用，也可以供 Node.js 应用单独安装。Vue 与 Python 共用 DTO 的定义来源，而不是共用某种语言的生成产物。

SDK 不绑定认证方式。`frontend/src/api/client.ts` 实现 SDK 的 `WorkstationTransport`，继续负责浏览器专属的 Cookie、CSRF、未登录事件和统一错误转换；Node.js 可以使用 SDK 的 Fetch Transport 注入 Bearer Token。`/api/auth/me`、`/api/auth/csrf` 和登录/注销仍是 Gateway BFF 接口，在浏览器适配层维护少量本地类型。

生成的 TypeScript DTO 只提供编译期约束，不替代 Backend 的运行时校验，也不用于表示页面状态、Tiptap 编辑状态、文件夹树节点或 IndexedDB 草稿。

### 2.5 测试和 CI

OpenAPI 用于：

- 校验契约文件是否合法。
- 检查 Backend 实际响应是否符合 Schema。
- 检测相对主分支的破坏性变更。
- 重复生成 Python Client 并检查生成结果是否漂移。
- 重复生成并构建 TypeScript SDK，检查源码和发布产物是否漂移。
- 对 TypeScript SDK 和 Vue API 适配层执行类型检查。
- 生成可浏览的 API 文档。

## 3. 文件组织

计划采用以下目录：

```text
openapi/
  workstation.yaml              HTTP API 契约真源
  README.md                     修改和生成命令
  examples/                     较大的请求/响应示例，可选

clients/
  python/
    pyproject.toml
    README.md
    workstation_client/         生成代码，禁止手工修改
    tests/                       手写客户端测试
  python-auth/
    workstation_auth/           Client Credentials 认证薄层
    tests/                       认证薄层测试
  typescript/
    src/generated/schema.ts     OpenAPI 生成 DTO，禁止手工修改
    src/index.ts                业务 endpoint 和 Transport 接口
    src/fetch-transport.ts      Node/通用 Fetch Transport
    dist/                       可安装的 SDK 构建结果
    test/                       SDK 测试

frontend/src/api/
  client.ts                     浏览器 Cookie、CSRF 和 SDK Transport
  workstation.ts                SDK 接入及 Gateway BFF 接口

docs/
  openapi.md                    OpenAPI 项目说明
  openapi-python-client-plan.md 实施方案和背景决策
```

Python 生成器配置保存在 `openapi/python-client-config.yaml`，避免覆盖生成输出目录时删除配置源文件。认证薄层是独立的 `clients/python-auth` 包，因此重新生成 `clients/python` 不会覆盖认证代码。TypeScript 生成器及精确版本保存在 `clients/typescript/package.json` 和 `package-lock.json`，Vue 通过 `file:../clients/typescript` 引用 SDK。

第一版使用单个 `workstation.yaml`。只有文件变得难以维护时，才拆分 `paths/`、`schemas/` 和 `responses/`。

## 4. 契约变更流程

HTTP 接口发生变化时，采用契约优先流程：

```text
提出 API 变更
    │
    ▼
修改 openapi/workstation.yaml
    │
    ├── 规范校验
    ├── 兼容性检查
    └── 人工评审 Request/Response 和状态码
    │
    ▼
同步修改 Java Controller/DTO
    │
    ▼
重新生成 Python Client 和 TypeScript SDK
    │
    ▼
运行 Backend、契约及 Python 测试
    │
    ▼
提交契约、实现和生成结果
```

具体规则：

1. 先修改 OpenAPI，明确对调用方可见的变化。
2. 运行规范校验，拒绝无效 `$ref`、重复 `operationId` 等问题。
3. 与主分支契约比较，判断是否存在破坏性变化。
4. 同步修改 Backend Controller 和 DTO。
5. 业务行为变化仍在 Service 和测试中实现。
6. 使用固定版本的生成器重新生成 Python Client 和 TypeScript SDK。
7. 不允许在生成目录中手工修复模型或请求函数。
8. 运行测试后一起提交契约、Backend 实现、Python 生成结果和 TypeScript SDK。

### 4.1 初次建立契约

现有 API 已经先于 OpenAPI 存在，因此第一次采用以下过程：

```text
现有 Controller/DTO
    -> 提取初始 OpenAPI
    -> 人工核对并修正
    -> 解决时间、错误结构等歧义
    -> 冻结 workstation.yaml 1.0.0
    -> 此后切换为契约优先
```

自动提取只用于建立基线。未经人工核对的生成文档不能直接成为正式契约。

## 5. 运行时请求流程

OpenAPI 在开发和构建阶段生成契约、客户端和文档；运行时并不代替 Gateway、Backend 或 Keycloak。

### 5.1 Python Client 获取 Token

Python Client 使用 OAuth2 Client Credentials：

```text
Python Client
    │ client_id + client_secret
    ▼
Keycloak Token Endpoint
    │
    ▼
短期 Access Token
```

Keycloak 中为 Python Client 创建独立的机密客户端和 Service Account。签发的 Token 必须满足 Backend 当前校验条件：

- 签发者是 Workstation Realm。
- `aud` 包含 `workstation-api`。
- Service Account 拥有 Backend 所要求的 `OWNER` 角色。
- Token 未过期且签名有效。

Client Secret 只能通过环境变量、Secret 文件或 Secret Manager 提供，不能写入：

- OpenAPI 文件。
- Python 源码。
- 客户端配置样例的真实值。
- Git 仓库。
- 日志。

Python Client 缓存短期 Token，并在过期前刷新；Client Credentials 没有浏览器 Session，也不使用 Refresh Token 时，可以重新调用 Token Endpoint 获取新 Token。

### 5.2 Python 经过 Gateway 调用 API

```text
Python Client
    │ Authorization: Bearer <token>
    ▼
Gateway
    │ 验证或保留 Bearer Token
    │ 不创建浏览器 Session
    │ 不要求 CSRF Token
    ▼
Backend Resource Server
    │ 再次校验签名、Issuer、Audience、有效期、OWNER
    ▼
Controller -> Service -> Mapper -> MySQL
```

Python Client 继续通过 Gateway，不直接暴露 Backend。Gateway 是唯一公开 API 入口。

Gateway 对传入的 Python Bearer Token 必须保留原始 `Authorization` Header，不能用浏览器 Session 的 Token Relay 结果覆盖它。Backend 仍独立校验 Token，因此即使 Gateway 配置错误也不会绕过 Backend 的 Resource Server 边界。

### 5.3 浏览器调用 API

浏览器保持现有流程：

```text
Vue
    │ WORKSTATION_SESSION Cookie
    │ 写请求额外携带 CSRF Header
    ▼
Gateway Session + CSRF
    │ Token Relay
    ▼
Backend Bearer JWT 校验
```

浏览器不接触 Access Token；Python 不使用浏览器 Cookie。两类请求可以使用相同业务路径，但认证状态不能相互替代。

### 5.4 Gateway 的双认证要求

Gateway 需要同时支持：

| 调用方 | 认证凭证 | 写请求 CSRF | 转发方式 |
| --- | --- | --- | --- |
| Vue 浏览器 | `WORKSTATION_SESSION` Cookie | 必须 | Gateway Token Relay |
| Python Client | Bearer Access Token | 不需要 | 保留 Bearer Token |

CSRF 豁免必须以请求实际通过 Bearer Token 认证为前提，不能仅凭请求带有一个 `Authorization` Header 就跳过校验。

这一双认证能力已在 Gateway 配置中实现。由于当前验证环境没有运行完整 Docker 登录链路，仍需使用真实 Keycloak Client Credentials 做一次端到端烟测。

## 6. OpenAPI 中的认证表达

OpenAPI 使用 OAuth2 Client Credentials 描述 Python Client 的认证流程。示意配置如下：

```yaml
components:
  securitySchemes:
    workstationOAuth:
      type: oauth2
      flows:
        clientCredentials:
          tokenUrl: https://example.com/auth/realms/workstation/protocol/openid-connect/token
          scopes: {}

security:
  - workstationOAuth: []
```

`tokenUrl` 使用部署环境可访问的 Keycloak 公开地址，真实域名由发布方式决定。

`OWNER` 当前是 Keycloak Realm Role，不是 OAuth Scope，因此不把它伪装为 `scopes` 条目。角色要求写在 Security Scheme 或操作说明中，并由 Gateway/Backend 实际校验。

OpenAPI 可以准确描述 Client Credentials，但 Python 生成器不一定自动完成 Token 获取和续期。因此客户端中保留一个手写认证薄层：

```text
Credential Provider
    -> 调用 Keycloak Token Endpoint
    -> 缓存 Access Token
    -> 创建/更新生成的 AuthenticatedClient
```

浏览器 Session + 条件式 CSRF 很难用单一 OpenAPI Security Scheme 完整表达。业务契约以 Python 的 OAuth2 调用方式为主要机器可读认证定义，浏览器 BFF 流程通过本文件和 `ARCHITECTURE.md` 维护。

## 7. Schema 使用规则

### 7.1 Request 与 Response 分离

请求和响应不共用数据库 Entity，也不因为字段暂时相同就强行复用所有 Schema。

示例：

```text
NoteRequest             创建/完整更新输入
NoteSummaryResponse     列表和聚合场景，不含 content
NoteDetailResponse      详情、创建和更新响应，包含 content
```

### 7.2 必填、可选和可空

这三个概念必须分开：

- 必填：JSON 中必须出现该属性。
- 可选：JSON 中可以不出现该属性。
- 可空：属性出现时可以为 `null`。

例如 `NoteRequest.title` 和 `content` 必填且不可空；`folderId`、`projectId` 可以为 `null`；`tagIds` 应明确是必填数组还是允许缺省，不能依赖生成器猜测。

### 7.3 分页

当前列表分页规则：

- `page` 从 `0` 开始。
- 默认 `page = 0`。
- 默认 `size = 20`。
- `size` 范围为 `1..100`。
- 响应包含 `items`、`page`、`size`、`total`、`hasMore`。

Java 使用 `PageResponse<T>`，OpenAPI 使用具名 Schema：

- `NotePageResponse`
- `ProjectPageResponse`
- `TaskPageResponse`

这样可以得到稳定的 Python 类型，不依赖生成器对泛型的特殊处理。

### 7.4 枚举

第一版包括：

- `ProjectStatus`：`PLANNING`、`ACTIVE`、`PAUSED`、`COMPLETED`、`ARCHIVED`
- `TaskStatus`：`TODO`、`IN_PROGRESS`、`DONE`
- `TaskPriority`：`LOW`、`MEDIUM`、`HIGH`
- `NoteImportDuplicateStrategy`：`CREATE_NEW`、`SKIP_TITLE`

删除或重命名枚举值属于破坏性变更。新增枚举值也可能影响使用穷尽匹配的 Python 代码，应在发布说明中明确列出。

### 7.5 时间格式

API 时间统一采用带 Offset 的 RFC 3339 `date-time`，例如：

```text
2026-09-15T15:30:00+08:00
2026-09-15T07:30:00Z
```

禁止用不带时区的时间作为正式契约。Backend 实施时需要把当前 `LocalDateTime` API 表达调整为具有 Offset 的类型或等价序列化方式。数据库存储方式仍由数据库设计决定。

日期字段，例如任务 `dueDate`，使用 `format: date`，示例为 `2026-09-30`。

### 7.6 未知字段

按照已确认建议：

- Request Schema 使用 `additionalProperties: false`，帮助发现字段拼写错误。
- Backend 也应拒绝请求中的未知字段，保持实现与契约一致。
- Response Schema 第一版同样使用明确字段集合。
- Python Client 升级时通过契约兼容性检查和重新生成处理新增字段。

如果以后需要允许响应增加字段而不重新生成客户端，应同时调整契约、Python 模型和兼容性策略，不能只修改其中一层。

## 8. 错误结构

所有业务错误、Backend Security 错误和 Gateway Security 错误统一使用：

```yaml
ApiError:
  type: object
  additionalProperties: false
  required:
    - code
    - message
    - timestamp
  properties:
    code:
      type: string
    message:
      type: string
    timestamp:
      type: string
      format: date-time
```

当前业务异常已经返回 `timestamp`，Backend Security 和 Gateway Security 的 `401/403` 尚未返回该字段。实施第一版正式契约时需要统一。

第一版错误码至少包含：

- `BAD_REQUEST`
- `BUSINESS_ERROR`
- `DATA_INTEGRITY_ERROR`
- `NOT_FOUND`
- `UNAUTHENTICATED`
- `ACCESS_DENIED`

Python 代码通过 `status` 和 `code` 判断错误类型，不通过匹配中文 `message` 编写控制逻辑。

## 9. Python Client 生成与使用

### 9.1 生成器

Python Client 当前固定使用 `openapi-python-client 0.29.1`。生成器版本和配置必须随契约一起维护，禁止使用浮动的 `latest`。

评估重点：

- OpenAPI 3.1 Schema 支持。
- 可空字段和未知字段处理。
- 枚举生成方式。
- 同步及异步 HTTP API。
- OAuth2/Bearer Token 注入方式。
- 生成代码可读性。
- 运行时依赖规模。
- 重复生成是否稳定。

### 9.2 包约定

- Distribution：`workstation-api-client`
- Import package：`workstation_client`
- Python：不低于 3.11
- HTTP：优先 `httpx`
- 生成代码：提交仓库，但禁止手工修改
- 生成器及配置：锁定版本并提交仓库

### 9.3 调用层次

```text
Python 业务代码
    -> 手写 WorkstationClient/认证薄层
    -> OpenAPI 生成的 endpoint 函数和模型
    -> httpx
    -> Gateway
```

手写薄层可以负责：

- 从环境读取 Keycloak Client ID 和 Secret。
- 获取和缓存 Access Token。
- Token 到期后重新获取并安全重试一次。
- 设置 Base URL、超时和 User-Agent。
- 把生成器的底层错误转换成项目自定义异常。
- 分页迭代器等跨 endpoint 便利能力。

薄层不重新声明 Request/Response 数据类，避免形成第二套 Python 模型。

### 9.4 TypeScript SDK

独立 SDK 当前固定使用 `openapi-typescript 7.13.0` 和 TypeScript 5.9.3：

```powershell
cd clients/typescript
npm.cmd ci
npm.cmd run api:generate
npm.cmd run typecheck
npm.cmd run build
npm.cmd test
```

生成目标是 `src/generated/schema.ts`，构建目标是 `dist/`。两者提交仓库但禁止手工修改；业务 endpoint 和 Transport 抽象在 SDK 中维护，Vue 的 Session/CSRF 实现在 `frontend/src/api/client.ts`。HTTP 契约变化后必须同时重新生成 Python Client 和 TypeScript SDK。

## 10. 当前 API 中使用的契约

第一版 OpenAPI 覆盖：

| 模块 | 路径 | 主要 Schema |
| --- | --- | --- |
| Dashboard | `/api/dashboard` | `DashboardResponse` |
| Notes | `/api/notes/**` | `NoteRequest`、`NoteSummaryResponse`、`NoteDetailResponse`、导入模型 |
| Note folders | `/api/note-folders/**` | `NoteFolderRequest`、`NoteFolderResponse` |
| Projects | `/api/projects/**` | `ProjectRequest`、`ProjectResponse`、`ProjectDetailResponse` |
| Tasks | `/api/tasks/**` | `TaskRequest`、`TaskResponse`、状态模型 |
| Tags | `/api/tags/**` | `TagRequest`、`TagResponse` |
| 公共结构 | 所有业务 API | 分页 Schema、`ApiError`、认证定义 |

`/api/auth/me`、`/api/auth/csrf` 和浏览器登录/注销属于 Gateway BFF 接口。它们不是 Python Client 的 Client Credentials 调用入口，也不属于 Backend 业务契约，因此由 Vue 适配层维护本地类型；如果以后纳入 OpenAPI，应放入独立的浏览器 BFF 契约，避免生成给 Python 使用。

## 11. 兼容性和版本

`openapi` 字段表示采用的 OpenAPI 规范版本；`info.version` 表示 Workstation API 契约版本。两者不能混用。

契约版本采用语义化版本：

- PATCH：说明、示例等不影响调用方的修正。
- MINOR：兼容地增加 endpoint 或可选能力。
- MAJOR：存在调用方需要修改代码的不兼容变化。

默认视为破坏性变化的操作包括：

- 删除或重命名 endpoint、参数或字段。
- 改变字段 JSON 类型。
- 把可选请求字段改为必填。
- 改变字段可空性。
- 删除枚举值。
- 改变成功状态码或响应结构。
- 改变 `operationId` 导致生成的客户端 API 名称变化。

每个 endpoint 必须有稳定、唯一的 `operationId`。它既是接口标识，也是生成客户端和类型映射的稳定键。

## 12. CI 流程

OpenAPI 相关 CI 按以下顺序执行：

```text
校验 OpenAPI
    -> API 风格检查
    -> 与主分支做兼容性比较
    -> 临时目录生成 Python Client
    -> 生成并构建 TypeScript SDK
    -> 检查两类生成结果无漂移
    -> 安装 Python 包并运行测试
    -> Vue API 类型检查和构建
    -> Backend mvn.cmd verify
```

至少验证：

- OpenAPI 文件语法合法且 `$ref` 可解析。
- `operationId` 唯一且稳定。
- 实际成功响应符合 Schema。
- `400/401/403/404` 都符合统一 `ApiError`。
- 创建返回 `201`，删除返回 `204`。
- 分页参数和响应符合约定。
- Python 正确解析枚举、`null`、日期和带 Offset 时间。
- TypeScript SDK 与 Vue API 的 Request、Response、查询参数和枚举通过类型检查。
- Client Credentials Token 可以经过 Gateway 访问 Backend。
- 无 Token、错误 Audience、无 `OWNER`、过期 Token 均被拒绝。
- 带 Bearer Token 的 Python 写请求不需要 CSRF。
- 只有 Session Cookie、但缺少 CSRF 的浏览器写请求仍被拒绝。
- Gateway 转发 Python 请求时不覆盖原始 Bearer Token。

## 13. 已实施内容

1. 建立并人工审核 `openapi/workstation.yaml`。
2. 统一 RFC 3339 时间格式、未知字段行为和 `ApiError`。
3. 为 Gateway 增加 Bearer JWT 认证和安全转发，同时保留浏览器 BFF 流程。
4. 在 Keycloak 创建 Python Client Credentials 客户端及 Service Account 权限。
5. 固定 Python 和 TypeScript 生成器版本。
6. 生成 `clients/python`，增加认证薄层。
7. 建立独立 TypeScript SDK，将 Vue 浏览器 API 适配为 SDK Transport。
8. 添加 Gateway、Backend、契约、Python、TypeScript SDK 和 Vue 类型检查。
9. 接入 CI 的规范校验、兼容性检查和两类生成漂移检查。

## 14. 安全约束

- Python Client Secret 不进入 Git。
- 生产环境必须通过 HTTPS 访问 Gateway 和 Keycloak。
- 日志不得输出 Client Secret 或完整 Access Token。
- Gateway 不能因为存在任意 `Authorization` Header 就跳过 CSRF；必须先成功认证 Bearer Token。
- Backend 始终保留独立 JWT 校验，不能只信任 Gateway 添加的普通 Header。
- Python 的 Client Credentials 与浏览器 Gateway Client 使用不同的 Keycloak Client ID 和 Secret。
- 初始阶段 Python Service Account 按现有 Backend 要求授予 `OWNER`；未来若引入更细权限，应新增专用角色而不是复用 OAuth Scope 名称冒充授权规则。
- Token 使用较短有效期；Secret 泄露后必须可以独立轮换，不影响浏览器登录。

## 15. 与其他文档的关系

- [系统架构](../ARCHITECTURE.md)：整个 Browser、Gateway、Backend、Keycloak 和数据流架构。
- [OpenAPI 与 Python 客户端实施方案](openapi-python-client-plan.md)：选型背景、范围和分阶段计划。
- [当前状态](STATUS.md)：已完成功能、进行中事项和已知问题。
- [Keycloak + Gateway/BFF 实施说明](gateway-implementation-plan.md)：现有浏览器认证和 Gateway 设计。
- [SSO 登录流程](sso-login-flow.md)：Authorization Code、PKCE、Session、Token Relay 和 CSRF 的详细解释。

## 16. 参考资料

- OpenAPI Specification：https://spec.openapis.org/oas/
- OpenAPI 3.1.2：https://spec.openapis.org/oas/v3.1.2.html
- JSON Schema：https://json-schema.org/
- openapi-python-client：https://github.com/openapi-generators/openapi-python-client
- OpenAPI Generator Python：https://openapi-generator.tech/docs/generators/python/
- openapi-diff：https://github.com/OpenAPITools/openapi-diff
