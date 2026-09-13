# Workstation

Workstation 是一个面向个人开发者的单用户 Web 工作台，用于集中管理 Markdown 笔记、软件项目和项目任务。应用默认进入概览页，当前主要使用场景是笔记记录、组织和检索。

## 主要功能

- 使用 Tiptap 所见即所得编辑 Markdown 笔记
- 手动保存、已有笔记自动保存和 IndexedDB 待同步草稿恢复
- 多级文件夹树组织笔记，左侧选择后在右侧直接编辑
- 按标题/正文搜索笔记，按标签筛选
- 标签、项目关联和本机 Markdown 文件导入
- 项目资料管理、项目详情和任务看板
- Keycloak 单点登录、Gateway/BFF 服务端会话和 CSRF 防护

## 技术栈

- 后端：Java 17、Spring Boot 4.0.7、Spring Security Resource Server、MyBatis 4.0.1
- 网关：Spring Cloud Gateway 5、OAuth2 Client、Spring Session Redis
- 身份服务：Keycloak 26.7、OpenID Connect
- 前端：Vue 3.5、Vue Router 4、Vite 6、Tiptap 3
- 数据：MySQL 8（业务库与 Keycloak 独立数据库）、Redis 8

## 本地环境

推荐使用 Docker Compose 启动完整环境，需要 Docker Engine 和 Docker Compose。单独调试源码还需要 JDK 17、Maven 3.9、Node.js 20 LTS 和 npm。

## 初始化

1. 创建数据库：

```sql
CREATE DATABASE workstation
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

2. 在 MySQL 客户端执行建表脚本，路径按实际仓库位置调整：

```sql
SOURCE D:/dev/project/workstation/backend/src/main/resources/db/schema.sql;
```

如果数据库是旧版 `categories/category_id` 结构，请先备份数据，然后只执行一次迁移脚本，不要再执行 `schema.sql`：

```sql
SOURCE D:/dev/project/workstation/backend/src/main/resources/db/migrate-to-note-folders.sql;
```

任何由旧版 `schema.sql` 创建的现有数据库，还需要执行一次笔记版本凭证迁移。全新数据库不需要执行：

```sql
SOURCE D:/dev/project/workstation/backend/src/main/resources/db/add-note-version.sql;
```

3. 完整环境创建配置：

```powershell
Copy-Item .env.docker.example .env.docker
```

4. 编辑 `.env.docker`，替换全部 `replace-with-...` 值。Keycloak 会在首次启动时创建临时 Owner 账号，首次登录必须修改密码。

| 变量 | 说明 |
| --- | --- |
| `SERVER_PORT` | 后端端口 |
| `DB_URL` | MySQL JDBC 地址 |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 |
| `WORKSTATION_PUBLIC_URL` | 浏览器访问 Workstation 的完整外部地址 |
| `KEYCLOAK_CLIENT_SECRET` | Gateway 的 OIDC Client Secret |
| `WORKSTATION_OWNER_USERNAME` | Workstation 所有者登录名 |
| `WORKSTATION_OWNER_INITIAL_PASSWORD` | 首次登录临时密码 |
| `REDIS_PASSWORD` | Gateway Session Redis 密码 |
| `DASHBOARD_RECENT_LIMIT` | 概览页最近记录数量 |
| `NOTE_IMPORT_ALLOWED_ROOTS` | 后端允许读取 Markdown 的根目录 |
| `NOTE_IMPORT_MAX_FILE_BYTES` | 单个导入文件大小上限 |
| `VITE_API_BASE_URL` | 前端 API 前缀，示例为 `/api` |

## 安装与启动

启动完整环境：

```powershell
docker compose --env-file .env.docker up -d --build
```

访问 `http://localhost:8088`，点击“使用统一身份登录”。Keycloak Admin Console 位于 `http://localhost:8088/auth/admin/`。

调试源码时，可只启动 MySQL、Redis、Keycloak 数据库初始化任务和 Keycloak：

```powershell
docker compose --env-file .env.docker up -d db redis keycloak-db-init keycloak
```

然后参照 `backend/.env.example` 与 `gateway/.env.example` 把配置写入当前终端或 IDE 运行环境，再分别启动后端、网关和前端：

```powershell
cd backend
mvn.cmd spring-boot:run

cd ../gateway
mvn.cmd spring-boot:run

cd ../frontend
npm.cmd run dev
```

Vite 默认把 API/OAuth2 回调代理到 `http://localhost:8081`，把 `/auth` 代理到 `http://localhost:8090`。完整说明见 [Keycloak + Gateway/BFF 实施说明](docs/gateway-implementation-plan.md)。

## 常用命令

```powershell
# 后端测试、校验和打包
cd backend
mvn.cmd test
mvn.cmd verify
mvn.cmd package
```

```powershell
# 网关测试、校验和打包
cd gateway
mvn.cmd test
mvn.cmd verify
mvn.cmd package
```

```powershell
# 前端开发、构建和预览
cd frontend
npm.cmd run dev
npm.cmd run build
npm.cmd run preview
```

前端目前没有配置 `test`、`lint` 或 `format` 脚本。

`backend/` 包含 Maven Wrapper，Linux/CI 在该目录使用 `./mvnw`；Gateway 在 CI 中使用 Maven 3.9。当前后端 `mvnw.cmd` 在 Windows 上无法正常启动，因此本地命令使用已安装的 `mvn.cmd`。

## 项目文档

- [Agent 开发入口](AGENTS.md)
- [系统架构](ARCHITECTURE.md)
- [当前状态](docs/STATUS.md)
- [CI/CD 与容器部署](docs/ci-cd.md)
- [Keycloak + Gateway/BFF 实施说明](docs/gateway-implementation-plan.md)
- [可选 Nginx 本机代理](nginx/README.md)
