# Workstation

Workstation 是一个面向开发者、以 Workspace 为资源边界的多人 Web 工作台，用于管理 Markdown 笔记、软件项目和项目任务。应用默认进入当前 Workspace 的概览页，主要使用场景是知识记录、组织、检索和成员共享。

## 主要功能

- 使用 Tiptap 所见即所得编辑 Markdown 笔记
- 手动保存、自动保存、按版本检测并发冲突、三方合并与冲突处理、IndexedDB 草稿恢复、笔记回收站及[内容历史、差异与恢复](docs/note-history.md)
- 多级文件夹、跨项目/任务/笔记搜索、标签、项目关联和 Markdown 导入
- 项目资料、项目详情、任务看板、依据笔记关联，以及 GitHub Issue 创建任务、来源关联、持续跟踪及 Webhook 指定刷新
- GitHub/GitLab 数据源连接、多仓库关联、批量导入、手动与定时同步，以及近期开发情况快照；启用与升级见 [数据源流程](docs/integrations.md)
- Keycloak 单点登录、Gateway/BFF 服务端会话和 CSRF 防护
- 个人与共享 Workspace、成员管理、数据隔离和可靠异步权限审计
- 统一工作台导航、笔记专注模式，以及项目、任务、标签和数据源的按需编辑抽屉
- OpenAPI 3.1 契约、TypeScript SDK 和 Client Credentials Python Client

## 技术栈

- Backend：Java 17、Spring Boot 4、Spring Security、MyBatis、MySQL 8、RabbitMQ
- Gateway：Spring Cloud Gateway、OAuth2 Client、Spring Session Redis
- Scheduling：XXL-JOB、Transactional Outbox
- Identity：Keycloak 26、OpenID Connect
- Frontend：Vue 3、Vite、TypeScript、Tiptap
- API Client：OpenAPI 3.1、Python 3.11+、httpx、TypeScript/Node

## 本地首次启动

完整环境只需要 Docker Engine 和 Docker Compose：

```powershell
Copy-Item .env.docker.example .env.docker
New-Item -ItemType Directory -Force notes
```

编辑 `.env.docker`，替换全部 `replace-with-...` 值，然后启动：

```powershell
docker compose --env-file .env.docker up -d --build
docker compose --env-file .env.docker ps
```

访问 `http://localhost:8088` 并点击“使用统一身份登录”。首次登录使用 `.env.docker` 中的 `WORKSTATION_INITIAL_USERNAME` 和 `WORKSTATION_INITIAL_PASSWORD`，Keycloak 会要求立即修改临时密码。Keycloak Admin Console 位于 `http://localhost:8088/auth/admin/`。RabbitMQ 和 XXL-JOB 管理页面分别绑定在宿主机回环地址的 `15672` 和 `8089` 端口，不经过应用公网入口。

停止环境但保留数据：

```powershell
docker compose --env-file .env.docker down
```

不要在需要保留数据时执行 `docker compose down -v`。已有环境的数据升级、生产部署和回滚统一参见 [CI/CD 与容器部署](docs/ci-cd.md)。

## 本地源码调试

源码调试需要 JDK 17、Maven 3.9、Node.js 20 和 npm；生成 Python Client 还需要 Python 3.11+ 与 uv。

先启动基础设施：

```powershell
docker compose --env-file .env.docker up -d db redis rabbitmq xxl-job-admin keycloak
```

根据 `backend/.env.example` 和 `gateway/.env.example` 配置当前终端或 IDE，然后分别启动：

```powershell
cd backend
mvn.cmd spring-boot:run

cd ../gateway
mvn.cmd spring-boot:run

cd ../frontend
npm.cmd ci
npm.cmd run dev
```

Vite 默认把 `/api` 和 OAuth2 回调代理到 `http://localhost:8081`，把 `/auth` 代理到 `http://localhost:8090`。认证设计与流程见 [认证与授权说明](docs/authentication.md)。

## 常用开发命令

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
```

两个 Java 模块执行 `mvn.cmd verify` 后，分别在 `backend/target/site/jacoco/index.html` 和 `gateway/target/site/jacoco/index.html` 查看测试覆盖率。相同目录中的 `jacoco.xml` 供 CI 的 SonarQube 扫描读取。覆盖率只统计本次 Maven 测试实际执行到的代码，不代表功能已得到充分验证。

```powershell
# TypeScript SDK
cd clients/typescript
npm.cmd ci
npm.cmd run typecheck
npm.cmd run build
npm.cmd test

# Frontend
cd ../../frontend
npm.cmd ci
npm.cmd run typecheck
npm.cmd test
npm.cmd run dev
npm.cmd run build
npm.cmd run preview
```

OpenAPI 和 Python Client 的生成、检查命令只在 [OpenAPI 使用说明](docs/openapi.md) 中维护。前端 `test` 使用 Node Test Runner 验证导航、页面实例边界和表单提交状态；`typecheck` 执行 TypeScript/Vue 静态类型检查。前端目前没有 `lint` 或 `format` 命令。

手动初始化 MySQL 时，依次执行 `backend/src/main/resources/db/schema.sql`、`backend/src/main/resources/db/migrate-project-integrations.sql` 和 `backend/src/main/resources/db/migrate-note-revisions.sql`；Compose 首次启动会自动按此顺序执行。已有数据卷的升级流程见 [CI/CD 与部署](docs/ci-cd.md)。

## 文档

所有当前文档、历史决策记录及其维护边界见 [文档导航](docs/README.md)。

## 许可证

本项目采用 [MIT 许可证](LICENSE)。第三方依赖及随附的第三方代码继续遵循各自的许可证，随附声明见 [第三方许可说明](THIRD_PARTY_NOTICES.md)。
