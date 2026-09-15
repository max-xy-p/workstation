# Workstation OpenAPI 与 Python 客户端实施方案

> 状态：已实施，完整 Keycloak/Gateway 运行链路待环境烟测
>
> 编写日期：2026-09-15
>
> 范围：使用 OpenAPI 统一维护 Workstation 的 HTTP API 契约，并生成 Python 客户端。本方案不改变数据库领域模型、业务规则或事务边界。

## 1. 背景

Workstation 当前由 Vue 浏览器客户端调用 Java/Spring Boot 后端，后续还需要由 Python 程序访问同一套业务 API。为了避免 Java DTO、JavaScript 调用代码和 Python 数据模型分别维护后逐渐产生差异，计划引入 OpenAPI 作为语言无关的 HTTP 契约。

OpenAPI 只描述服务如何通过 HTTP 被调用，包括路径、参数、请求体、响应体、状态码和认证方式。数据库结构、领域行为和业务规则继续由现有后端负责。

## 2. 目标

本方案需要达到以下目标：

1. 使用一份可版本控制、可评审的 OpenAPI 文档定义业务 API。
2. 统一维护 Request、Response、分页、错误响应和枚举等 HTTP 数据结构。
3. 从 OpenAPI 生成带类型的 Python 客户端，避免手写重复的数据类和请求代码。
4. 保留 Spring Boot 的 `Controller -> Service -> Mapper` 分层和 MySQL 数据模型。
5. 在 CI 中发现无效契约、生成结果漂移和不兼容变更。
6. 明确浏览器客户端与 Python 客户端不同的认证方式。

## 3. 非目标

以下内容不交给 OpenAPI 生成或实施：

- MySQL 表、字段、索引、外键和迁移脚本。
- Java Entity、MyBatis Mapper 和 Mapper 查询对象。
- Service 中的事务边界和关联对象存在性校验。
- 文件夹树的循环校验、同级名称约束和非空删除限制。
- 项目、任务、笔记和标签的删除规则。
- `notes.version` 的递增逻辑。
- IndexedDB 草稿何时清理及草稿恢复策略。
- Markdown 导入路径的白名单检查和文件读取行为。
- Keycloak 用户、角色和客户端配置本身。

OpenAPI 可以在字段或操作的 `description` 中说明这些行为，但说明不代替后端实现和测试。

## 4. 契约的职责边界

| 层次 | 唯一维护位置 | OpenAPI 职责 |
| --- | --- | --- |
| HTTP 路径与方法 | `openapi/workstation.yaml` | 定义 |
| Path、Query、Header 参数 | `openapi/workstation.yaml` | 定义 |
| Request/Response Schema | `openapi/workstation.yaml` | 定义 |
| 分页与错误响应 | `openapi/workstation.yaml` | 定义 |
| HTTP 状态码和媒体类型 | `openapi/workstation.yaml` | 定义 |
| 认证要求 | `openapi/workstation.yaml` | 描述接口要求 |
| Java API DTO | 后端 API 边界代码 | 实现契约 |
| Python API 模型与调用函数 | 生成目录 | 从契约生成 |
| 数据库模型 | 数据库迁移脚本 | 不参与 |
| 领域规则和事务 | Java Service、数据库约束、测试 | 不参与 |

### 4.1 关于“唯一真源”

OpenAPI 是 HTTP 边界的唯一真源，不是整个系统数据模型的唯一真源。

例如，`NoteDetailResponse.version` 的字段名称、JSON 类型和是否必填由 OpenAPI 定义；“成功更新后版本必须递增”由 `NoteService`、`NoteMapper` 和测试保证。Python 客户端能够读取 `version`，但不能仅凭生成模型保证递增行为。

## 5. 建议的总体结构

```text
openapi/workstation.yaml
        │
        ├── 契约校验和兼容性检查
        │
        ├── Java Backend 实现并接受契约测试
        │
        └── 生成 clients/python/workstation_client
                         │
                         └── Python 应用或自动化脚本
```

建议目录：

```text
openapi/
  workstation.yaml                  OpenAPI 主契约
  README.md                         契约维护与生成说明
  examples/                         必要时存放较大的请求/响应示例

clients/
  python/
    pyproject.toml
    README.md
    workstation_client/             生成代码，不手工修改
    tests/                           少量手写集成/烟测代码

docs/
  openapi-python-client-plan.md      本方案
```

第一版优先使用单个 `workstation.yaml`。只有当文件明显过大时，再按 `paths/` 和 `schemas/` 拆分，避免过早增加 `$ref` 路径管理成本。

## 6. OpenAPI 版本与书写原则

建议第一版使用 OpenAPI 3.1.x，并锁定一个明确的补丁版本。实施时需要同时锁定校验器和 Python 生成器版本，不能在 CI 中使用浮动的 `latest`。

契约遵循以下原则：

- 每个操作必须有稳定且唯一的 `operationId`，因为它会影响生成的 Python 函数名。
- 按 `dashboard`、`notes`、`note-folders`、`projects`、`tasks`、`tags` 设置 operation tag。
- Request 和 Response 使用不同的 Schema，不把数据库 Entity 暴露为 API Schema。
- 明确区分“属性可以缺省”和“属性值可以为 `null`”。
- 数组响应明确规定是否始终返回数组；当前 DTO 中的集合建议始终返回数组而不是 `null`。
- 所有成功和失败状态码都显式声明。
- 创建接口声明 `201`，删除接口声明 `204`。
- JSON 请求和响应声明 `application/json`；`204` 不声明响应体。
- ID 使用 `integer`、`format: int64`。
- 日期使用 `string`、`format: date`。
- 时间字段在完成第 12.2 节的时区决策后统一表达。
- 字段长度等结构性限制同时写入 Schema，例如标题 `maxLength: 200`、标签名 `maxLength: 80`。
- 业务存在性和跨字段规则只写说明，不伪装成 Schema 可以执行的校验。

## 7. 第一版契约范围

第一版覆盖现有业务接口，不顺便新增业务功能。

### 7.1 Dashboard

- `GET /api/dashboard`
- `DashboardResponse`

### 7.2 Notes

- `GET /api/notes`
- `GET /api/notes/{id}`
- `POST /api/notes`
- `PUT /api/notes/{id}`
- `DELETE /api/notes/{id}`
- `POST /api/notes/import`
- `NoteRequest`
- `NoteSummaryResponse`
- `NoteDetailResponse`
- `NoteImportRequest`
- `NoteImportResponse`
- `NoteImportItemResponse`
- `NoteImportDuplicateStrategy`

`POST /api/notes/import` 中的 `sourcePath` 是 Backend 所在机器或容器可访问的路径，不是 Python 客户端本机路径。契约必须明确这一点，以免 Python 调用者误以为接口会上传本地文件。

### 7.3 Note folders

- `GET /api/note-folders`
- `GET /api/note-folders/{id}`
- `POST /api/note-folders`
- `PUT /api/note-folders/{id}`
- `DELETE /api/note-folders/{id}`
- `NoteFolderRequest`
- `NoteFolderResponse`

OpenAPI 只表达 `parentId` 字段，不负责证明父文件夹存在、阻止循环或执行删除限制。

### 7.4 Projects

- `GET /api/projects`
- `GET /api/projects/{id}`
- `POST /api/projects`
- `PUT /api/projects/{id}`
- `PATCH /api/projects/{id}/status`
- `DELETE /api/projects/{id}`
- `ProjectRequest`
- `ProjectStatusRequest`
- `ProjectSummaryResponse`
- `ProjectResponse`
- `ProjectDetailResponse`
- `ProjectStatus`

### 7.5 Tasks

- `GET /api/tasks`
- `GET /api/tasks/{id}`
- `POST /api/tasks`
- `PUT /api/tasks/{id}`
- `PATCH /api/tasks/{id}/status`
- `DELETE /api/tasks/{id}`
- `TaskRequest`
- `TaskStatusRequest`
- `TaskResponse`
- `TaskStatus`
- `TaskPriority`

### 7.6 Tags

- `GET /api/tags`
- `GET /api/tags/{id}`
- `POST /api/tags`
- `PUT /api/tags/{id}`
- `DELETE /api/tags/{id}`
- `TagRequest`
- `TagResponse`

### 7.7 公共结构

- `ApiError`
- 具名分页响应，如 `NotePageResponse`、`ProjectPageResponse` 和 `TaskPageResponse`
- 公共的 ID Path Parameter
- 公共的 `400`、`401`、`403`、`404` 响应

虽然 Java 使用泛型 `PageResponse<T>`，但 OpenAPI 和不同语言生成器对泛型抽象的处理不一致。契约中建议输出具名分页 Schema，并复用公共分页字段，从而得到稳定、易读的 Python 类型。

## 8. Python 客户端方案

### 8.1 生成器选择

第一候选建议使用 `openapi-python-client`：

- 专门面向 Python 客户端。
- 支持 OpenAPI 3.0 和 3.1。
- 生成同步/异步客户端、类型模型和按 tag 组织的 API 模块。
- 生成结果相对适合直接作为普通 Python 包使用。

备选是 OpenAPI Generator 的 `python` generator。它的生态更大、配置更多，但生成结果和运行时抽象通常更重。正式实施前应使用 Workstation 第一版契约分别生成一次，以生成代码的可读性、可空字段处理、认证扩展方式和依赖规模作为最终选择依据。

不建议自行编写模板生成 Python 数据类和请求函数，这会形成需要长期维护的第三套契约工具。

### 8.2 生成包约定

建议使用以下名称：

- Distribution：`workstation-api-client`
- Import package：`workstation_client`
- Python 最低版本：在实施时明确并固定，建议不低于 Python 3.11。
- HTTP 实现：优先使用 `httpx`，同时保留同步和异步使用方式。

生成代码必须满足：

- 不手工编辑 `workstation_client` 下的生成文件。
- 生成器版本和配置文件进入版本控制。
- 相同输入必须产生可重复结果。
- 客户端包版本与 API 契约版本建立明确映射。
- 在生成代码之外编写认证、重试或高层业务封装。

### 8.3 是否提交生成代码

本项目第一阶段建议提交 `clients/python` 的生成结果，原因是它便于本地直接安装、审核生成差异和复现问题。CI 应在临时目录重新生成并与已提交结果比较；出现差异时失败。

如果以后通过私有包仓库或 GitHub Release 发布客户端，可以重新评估是否只提交契约和生成配置。

### 8.4 高层封装

生成客户端只负责 HTTP 和序列化。可以在生成目录外增加薄封装，例如：

```python
from workstation_client import AuthenticatedClient


def create_client(base_url: str, access_token: str) -> AuthenticatedClient:
    return AuthenticatedClient(
        base_url=base_url,
        token=access_token,
        timeout=30.0,
    )
```

Token 获取、刷新、日志脱敏和应用配置不应写入生成模板，避免每次重新生成时丢失定制。

## 9. Python 客户端认证设计

### 9.1 当前浏览器链路

当前浏览器使用：

```text
Browser Session Cookie + CSRF
  -> Gateway
  -> Token Relay
  -> Backend Bearer JWT
```

这套流程是浏览器 BFF 模式。Python 客户端不应抓取登录页面、模拟浏览器 Session 或从 Cookie 中间接管理认证。

### 9.2 已确认的 Python 链路

Python 客户端按已确认方案单独配置 Keycloak Machine-to-Machine Client，使用 Client Credentials 获取短期 Access Token：

```text
Python Client
  -> Keycloak Token Endpoint
  -> Access Token（audience 包含 workstation-api，角色包含 OWNER）
  -> 受 TLS 保护的 Backend API 入口
```

Python 请求携带：

```http
Authorization: Bearer <access-token>
```

这条链路不使用浏览器 Cookie，因此也不需要 CSRF Token。Backend 继续校验 JWT 签名、Issuer、Audience、有效期和 `OWNER` 角色。

不建议使用 Resource Owner Password Credentials，即让 Python 客户端直接保存 Workstation 用户名和密码。

### 9.3 网络入口仍需设计

当前生产部署只公开 Web/Nginx 入口，Gateway 的 `/api/**` 路由面向浏览器 Session，并通过 Token Relay 访问 Backend；Backend 没有直接暴露到宿主机公网。

已确认 Python 客户端继续通过 Gateway 访问 Backend，不直接暴露 Backend。Gateway 需要增加明确的 Bearer Token API 链路：对 Python Token 完成认证或受控透传，不创建浏览器 Session，不要求 CSRF，并保持浏览器 Session/CSRF/Token Relay 链路不变。该改动属于认证与部署设计，不由 OpenAPI 自动完成。

OpenAPI 中可以声明 `bearerAuth`，但 Token 获取和网络暴露方案必须由 Keycloak、Gateway、Nginx 与 Backend 配置落实。

## 10. 错误模型

建议统一错误响应为：

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

当前需要先处理一个实现差异：

- `GlobalExceptionHandler` 返回 `code`、`message`、`timestamp`。
- Backend Security 和 Gateway Security 返回的 `401/403` 只有 `code`、`message`。

冻结契约前建议统一所有错误响应。若第一阶段不修改认证错误实现，则 `timestamp` 只能暂时定义为非必填，但这会削弱客户端模型的一致性。

第一版至少覆盖这些错误码：

- `BAD_REQUEST`
- `BUSINESS_ERROR`
- `DATA_INTEGRITY_ERROR`
- `NOT_FOUND`
- `UNAUTHENTICATED`
- `ACCESS_DENIED`

错误码是稳定的机器判断依据；`message` 可以用于日志或展示，但 Python 程序不应通过匹配中文 `message` 判断错误类型。

## 11. 版本与兼容性策略

OpenAPI 文档中的 `info.version` 表示 API 契约版本，不等同于 OpenAPI 规范自身的 `openapi` 版本。

建议采用语义化版本：

- PATCH：描述、示例等不影响调用方的修正。
- MINOR：增加可选字段、新 endpoint 或新的可选能力。
- MAJOR：删除/重命名字段、改变字段类型、增加必填请求字段、删除 endpoint 等不兼容变化。

以下变更默认视为破坏性变化：

- 删除或重命名 endpoint、参数、请求字段或响应字段。
- 改变 JSON 字段类型或可空性。
- 把可选请求字段改为必填。
- 删除枚举值。
- 改变 `operationId`，因为它可能改变 Python 函数名。
- 改变成功状态码或响应结构。

新增响应字段通常对宽松 JSON 客户端兼容，但可能破坏严格禁止未知字段的客户端。因此需要在第一版中统一决定 `additionalProperties` 策略，并让 Java 与 Python 的反序列化行为保持一致。

## 12. 已确认的第一版契约决策

### 12.1 认证方式

Python 客户端使用 Client Credentials，并继续通过 Gateway 访问 Backend。Gateway 对 Python Bearer Token 不执行浏览器 CSRF 校验，并保留 Token 供 Backend 独立验证。

### 12.2 时间格式

当前 Java Response 使用 `LocalDateTime`，序列化结果不携带时区或 UTC Offset；OpenAPI/JSON Schema 的标准 `date-time` 通常按 RFC 3339 表达，Python 生成器可能把它解释为带明确时间语义的 `datetime`。

已确认 API 时间统一输出带 Offset 的 RFC 3339 时间，例如 `2026-09-15T10:30:00+08:00` 或 UTC `2026-09-15T02:30:00Z`。

此选择只涉及 API 表达，不要求 OpenAPI 管理数据库字段。

### 12.3 未知字段策略

已确认请求体拒绝未知字段，尽早发现 Python 调用方拼写错误；第一版响应体也使用明确字段集合。

若契约使用 `additionalProperties: false`，后端 Jackson 配置和 Python 生成模型必须与其一致，否则会出现“文档拒绝但服务端接受”或相反的漂移。

### 12.4 错误响应时间戳

已确认业务错误、Backend Security 错误和 Gateway Security 错误都返回同一个完整 `ApiError`，`timestamp` 为必填。

### 12.5 生成代码入库

已确认第一阶段提交生成代码，并由 CI 检查生成差异。

## 13. 分阶段实施

### 阶段一：提取当前契约基线

1. 从现有 Controller、DTO 和枚举整理初始 OpenAPI 文档。
2. 把人工审核后的静态契约提交为 HTTP API 真源，不把运行时生成结果作为第二份真源。
3. 人工核对路径、参数、状态码、必填/可空、字段长度和示例。
4. 修复第 12 节中的契约歧义。
5. 将审核后的契约保存为 `openapi/workstation.yaml`。

这一阶段允许使用当前 Java 实现帮助建立基线，但生成出来的文档不能未经审核直接成为正式契约。

### 阶段二：建立契约优先流程

1. 后续 HTTP Request/Response 变更先修改 OpenAPI。
2. 在同一变更中同步 Java DTO/Controller 实现。
3. 从新契约重新生成 Python 客户端。
4. 运行后端测试、契约测试和 Python 客户端测试。
5. CI 比较基准契约，拒绝未明确处理的不兼容变化。

### 阶段三：接入 Python 客户端

1. 确认 Python 认证流程和 API 网络入口。
2. 生成 `workstation-api-client`。
3. 编写 Token 获取与刷新薄封装。
4. 添加读取、创建、更新、分页和错误解析烟测。
5. 验证 Token 过期、缺少 `OWNER`、错误 Audience 和非法请求体。

### 阶段四：发布与演进

1. 为契约和 Python 包建立版本映射。
2. 需要跨仓库使用时发布版本化 Python 包。
3. 在发布说明中列出兼容和不兼容 API 变化。
4. 只有确有必要时再引入 `/api/v2`；普通兼容新增不创建新路径版本。

## 14. CI 建议

契约相关 CI 至少包含：

1. 校验 `workstation.yaml` 语法和 OpenAPI 规范合法性。
2. 执行项目约定的 API 风格检查。
3. 与主分支契约比较，检测破坏性变更。
4. 在临时目录生成 Python 客户端。
5. 比较生成结果，确保仓库中的客户端没有漂移。
6. 安装生成的 Python 包并运行最小导入测试。
7. 运行 Backend `mvn.cmd verify`。

当前 Frontend 没有 `test`、`lint` 或 `format` 命令，本方案不把这些未存在的命令列为验收项。

建议另外增加契约测试，至少确认：

- 实际成功响应可以被对应 OpenAPI Schema 接受。
- `400/401/403/404` 实际结构与 `ApiError` 一致。
- `201`、`204` 等状态码与契约一致。
- 分页从 0 开始，默认 `20`，允许范围 `1..100`。
- Python 可以正确解析枚举、`null`、日期和时间。

## 15. 完成标准

满足以下条件后，可以认为第一版 OpenAPI 与 Python 客户端落地完成：

- `openapi/workstation.yaml` 覆盖全部现有业务 endpoint。
- Request、Response、分页、枚举和错误结构都有独立 Schema。
- Java Controller/DTO 的实际行为与契约一致。
- 文件夹规则、事务和版本递增仍由 Service/Mapper/测试负责，没有迁入生成代码。
- Python 客户端可以从固定版本的契约重复生成。
- Python 客户端可以完成至少一次认证后的读、写和分页调用。
- `401/403` 不依赖浏览器 Session 或 CSRF 流程。
- CI 能发现契约无效、生成结果漂移和破坏性变更。
- 生成目录中没有手工维护的业务逻辑。

## 16. 审核结论

审核本方案时，建议明确记录以下决定：

- [x] OpenAPI 只作为 HTTP 契约真源。
- [x] 第一版采用 OpenAPI 3.1.x。
- [x] 优先试用 `openapi-python-client`，并与 OpenAPI Generator 做一次实际生成对比。
- [x] 第一阶段提交 Python 生成代码并由 CI 检查漂移。
- [x] Python 客户端使用 Client Credentials。
- [x] Python 客户端继续通过 Gateway 访问 Backend。
- [x] API 时间统一为带 Offset 的 RFC 3339 `date-time`。
- [x] Request 拒绝未知字段，第一版 Response 也使用明确字段集合。
- [x] `ApiError.timestamp` 在所有错误响应中统一为必填。
- [x] 独立 TypeScript SDK 从同一契约生成 DTO 和业务 endpoint，Vue 注入浏览器 Session/CSRF Transport。
- [x] Python Client 与 TypeScript SDK 都提交仓库并由 CI 检查生成漂移。

## 17. 参考资料

- OpenAPI Specification：https://spec.openapis.org/oas/
- OpenAPI 3.1.2：https://spec.openapis.org/oas/v3.1.2.html
- JSON Schema：https://json-schema.org/
- openapi-python-client：https://github.com/openapi-generators/openapi-python-client
- OpenAPI Generator Python：https://openapi-generator.tech/docs/generators/python/
- openapi-diff：https://github.com/OpenAPITools/openapi-diff
