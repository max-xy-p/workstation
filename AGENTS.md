# AGENTS.md

## 项目概览

Workstation 是一个单用户个人工作台，包含多级文件夹 Markdown 笔记、项目资料和任务看板。概览页是默认入口，笔记使用独立工作区。Backend 的 MySQL 数据是持久化真源；浏览器 IndexedDB 只保存尚未获得服务端确认的草稿。

## 技术栈

- Java 17、Maven 3.9、Spring Boot 4、Spring Security、MyBatis、MySQL
- Spring Cloud Gateway、Keycloak、Redis、OAuth2/OIDC
- Vue 3、Vue Router、Vite、TypeScript、Tiptap、Lucide
- OpenAPI 3.1、Python 3.11+、uv、httpx
- JUnit 5、Mockito、MockMvc、Node Test Runner

## 仓库结构

```text
backend/                                     Backend、Maven、数据库脚本和容器配置
backend/src/main/java/com/mike/workstation/  后端业务代码
backend/src/test/                            后端测试
gateway/                                     Gateway/BFF、认证配置和测试
frontend/                                    Vue 应用、浏览器 API 适配和 Web 容器
openapi/                                     HTTP API 契约和生成器配置
clients/python/                              OpenAPI 生成的 Python Client
clients/python-auth/                         Python Client Credentials 认证薄层
clients/typescript/                          独立 TypeScript/Node SDK
keycloak/                                    Realm 首次导入模板
.github/workflows/                           CI、SDK 发布和应用发布流程
docs/                                        面向人的流程与进度文档
nginx/                                       可选的本机 Nginx 配置
```

## 开发前必读

- 本地运行入口：`README.md`
- 系统边界和数据流：`ARCHITECTURE.md`
- 文档职责：`docs/README.md`
- 当前进度和已知问题：`docs/STATUS.md`
- 认证与授权说明：`docs/authentication.md`
- OpenAPI 变更流程：`docs/openapi.md`
- SDK 分发流程：`docs/sdk-distribution.md`
- CI/CD 和部署流程：`docs/ci-cd.md`

开始开发前阅读与任务相关的文档和源码。文档与代码冲突时，以当前代码为准，并在同一变更中修正文档。

## 通用工作规则

- 不修改或回退与当前任务无关的未提交变更。
- 先确认职责层和数据真源，再修改代码；不要在调用方重复实现服务端规则。
- 行为变化必须补充与风险相称的测试，并运行受影响模块的验证。
- 新增配置时同步示例配置、Compose、测试和部署流程；不得提交真实 Secret。
- 不建立第二套 DTO、认证状态、草稿存储或业务枚举。
- 不通过手工修改生成文件、关闭检查或放宽安全规则来绕过失败。

## Backend 规范

- 沿用 `Controller -> Service -> Mapper` 分层。
- Controller 只处理 HTTP 参数、状态码和 DTO，不承载事务或持久化规则。
- Service 负责业务校验、关联对象存在性和事务；写操作使用 `@Transactional`。
- Mapper 使用 MyBatis 注解 SQL，并避免列表场景的逐条关联查询。
- Java 领域对象使用 Lombok `@Data`；请求和组合响应优先使用 Java `record`。
- 数据库 Entity、Request DTO 和 Response DTO 分离，Entity 不直接作为 API Response。
- API 位于 `/api` 并使用 JSON；创建返回 `201`，删除返回 `204`。
- 业务异常统一由 `GlobalExceptionHandler` 转换为契约规定的错误结构。
- 历史数据库变更提供独立迁移脚本；不要假设已有数据卷会重新执行初始化脚本。
- Backend 测试沿用 JUnit 5、Mockito 和 MockMvc。Service 规则变化补 Service 测试，HTTP 契约变化补 Controller 或集成测试。

## OpenAPI 与 SDK 规范

- `openapi/workstation.yaml` 是 HTTP Request/Response、分页、错误结构和 endpoint 的唯一契约真源。
- OpenAPI 不负责文件夹层级、删除限制、事务、关联校验、`notes.version` 递增或 IndexedDB 草稿策略。
- 调用方可见的接口变化必须先修改 OpenAPI，再修改 Backend，并重新生成客户端。
- `operationId`、字段名称、状态码和可空性应保持稳定；破坏性变化必须明确评估版本影响。
- `clients/python/` 由 `openapi-python-client` 生成，不手工修改其源码、README 或包配置。
- `clients/typescript/src/generated/schema.ts` 和 `clients/typescript/dist/` 是生成内容，不手工修改。
- Python 手写认证代码只放在 `clients/python-auth/`；TypeScript 手写 endpoint 和 Transport 放在生成目录之外。
- Frontend 通过 `clients/typescript` 使用业务 DTO 和 endpoint，不在 `frontend` 中重新声明同一套业务类型。
- OpenAPI、Python SDK、Python 认证层和 TypeScript SDK 的语义化版本必须一致；认证层精确依赖同版本 Python SDK。
- 发布前运行 `scripts/check_sdk_versions.py`；`sdk-v<版本>` 必须与契约版本一致。
- `sdk-v*` 只发布 SDK，`v*` 只发布应用镜像，两者不得互相触发。
- 生成器版本和锁文件必须提交；重新生成后必须确认没有非预期漂移。

## 认证与安全规范

- 所有业务 API 都受认证保护；Web/Nginx 和 Gateway 是统一入口，不允许客户端绕过 Gateway 直连 Backend。
- 浏览器使用 Gateway Session Cookie；Access Token 和 Refresh Token 不进入浏览器存储。
- 浏览器写请求由 `frontend/src/api/client.ts` 自动携带 Cookie 和 CSRF Token。
- Python 服务使用独立 Keycloak Client Credentials，经 Gateway 携带 Bearer Token 调用 Backend。
- Gateway 只有在 Bearer Token 已成功认证后才能豁免 CSRF，不能只检查 `Authorization` Header 是否存在。
- Gateway 转发机器请求时保留原始 Bearer Token；Backend 始终独立校验签名、Issuer、Audience、有效期和 `OWNER` 角色。
- 浏览器 Gateway Client、Python Client 和其他机器服务使用不同的 Client ID 与 Secret。
- Secret、完整 Token、真实密码不得进入源码、OpenAPI、日志、镜像层或 GitHub Actions 输出。
- 生产入口必须使用 HTTPS，并正确转发 Host、Proto 和 Port。

## Frontend 与笔记规范

- Vue 页面使用 Composition API 与 `<script setup>`。
- 业务 API 统一通过 `frontend/src/api/workstation.ts` 调用；底层 Cookie、CSRF 和错误转换集中在 `frontend/src/api/client.ts`。
- Tiptap 适配集中在 `frontend/src/components/MarkdownEditor.vue`；正文持久化为 Markdown，不保存 HTML。
- IndexedDB 访问集中在 `frontend/src/utils/noteDraftStore.js`，只保存待确认草稿，不能代替 MySQL。
- `notes.version` 是持久化确认凭证；每次更新必须递增，Frontend 只有校验响应版本后才能清除草稿。
- 文件夹通过 `note_folders.parent_id` 自关联；循环、层级和删除约束由 `NoteFolderService` 负责。
- 沿用现有全局样式和页面结构；修改编辑器样式时检查通用选择器是否影响 ProseMirror 节点。

## Docker、CI 与发布注意事项

- Web 镜像构建上下文必须是仓库根目录，因为 Frontend 依赖 `clients/typescript`。
- `frontend/Dockerfile` 中的源文件路径相对于仓库根目录；Nginx 配置来源是 `frontend/nginx.conf`。
- CI 必须覆盖 Backend、Gateway、OpenAPI/Python、TypeScript SDK、Frontend 和三个应用镜像。
- 不在文档或交付说明中声称运行了项目没有配置的测试、lint 或 format。
- GHCR 应用镜像使用不可变版本标签；生产部署不依赖 `latest`。
- SDK 仓库读取凭证、GHCR 拉取凭证和 Keycloak 运行凭证用途不同，不得混用。
- 数据库、Realm 或数据卷操作前先确认目标和备份；不得用容器重建代替迁移。

## 文档与文字规范

- `docs/` 只维护面向人的流程、边界和进度，不复制代码、完整配置、工作流 YAML 或大段命令。
- 开发规范、实现限制和注意事项只维护在本文件。
- 当前进度、验证结果和已知问题只维护在 `docs/STATUS.md`。
- 已完成且不再维护的方案文档直接删除，不保留兼容副本。
- 项目维护的文档、手写代码注释、文档字符串和面向使用者的错误消息使用中文。
- 类名、函数名、协议字段和第三方专有名词按代码约定保留。
- OpenAPI 生成目录中的固定模板文本不手工翻译，以免重新生成时丢失或产生漂移。

## 常用验证命令

```powershell
# Backend
cd backend
mvn.cmd test
mvn.cmd verify
mvn.cmd package

# Gateway
cd ../gateway
mvn.cmd test
mvn.cmd verify
mvn.cmd package

# TypeScript SDK
cd ../clients/typescript
npm.cmd ci
npm.cmd run api:generate
npm.cmd run typecheck
npm.cmd run build
npm.cmd test

# Frontend
cd ../../frontend
npm.cmd ci
npm.cmd run typecheck
npm.cmd run build

# Python Client Credentials 认证层
cd ..
uv sync --project clients/python-auth --locked
uv run --project clients/python-auth python -m unittest discover -s clients/python-auth/tests -v

# SDK 版本一致性
python scripts/check_sdk_versions.py
```

Frontend 当前没有 `test`、`lint` 或 `format` 命令。
