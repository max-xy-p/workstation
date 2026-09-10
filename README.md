# Workstation

Workstation 是一个面向个人开发者的单用户 Web 工作台，用于集中管理 Markdown 笔记、软件项目和项目任务。应用默认进入笔记列表，当前主要使用场景是笔记记录、组织和检索。

## 主要功能

- 使用 Tiptap 所见即所得编辑 Markdown 笔记
- 手动保存、已有笔记自动保存和浏览器本地草稿恢复
- 多级文件夹树组织笔记，左侧选择后在右侧直接编辑
- 按标题/正文搜索笔记，按标签筛选
- 标签、项目关联和本机 Markdown 文件导入
- 项目资料管理、项目详情和任务看板
- 单管理员 Session 登录和 CSRF 防护

## 技术栈

- 后端：Java 17、Spring Boot 4.0.7、Spring Security、MyBatis 4.0.1
- 前端：Vue 3.5、Vue Router 4、Vite 6、Tiptap 3
- 数据库：MySQL 8、InnoDB、utf8mb4

## 本地环境

本地开发不需要 Docker，需要安装 JDK 17、Maven 3.9、MySQL 8.x、Node.js 20 LTS 和 npm。

## 初始化

1. 创建数据库：

```sql
CREATE DATABASE workstation
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

2. 在 MySQL 客户端执行建表脚本，路径按实际仓库位置调整：

```sql
SOURCE D:/dev/project/workstation/src/main/resources/db/schema.sql;
```

如果数据库是旧版 `categories/category_id` 结构，请先备份数据，然后只执行一次迁移脚本，不要再执行 `schema.sql`：

```sql
SOURCE D:/dev/project/workstation/src/main/resources/db/migrate-to-note-folders.sql;
```

3. 创建本地配置：

```powershell
Copy-Item .env.example .env
Copy-Item frontend/.env.example frontend/.env
```

4. 编辑根目录 `.env`。`APP_ADMIN_PASSWORD_HASH` 必须填写 BCrypt 散列，不能填写明文密码。

| 变量 | 说明 |
| --- | --- |
| `SERVER_PORT` | 后端端口 |
| `DB_URL` | MySQL JDBC 地址 |
| `DB_USERNAME` / `DB_PASSWORD` | 数据库账号 |
| `APP_ADMIN_USERNAME` | 管理员用户名 |
| `APP_ADMIN_PASSWORD_HASH` | 管理员密码 BCrypt 散列 |
| `SESSION_COOKIE_SECURE` | HTTPS 部署时设为 `true` |
| `DASHBOARD_RECENT_LIMIT` | 概览页最近记录数量 |
| `NOTE_IMPORT_ALLOWED_ROOTS` | 后端允许读取 Markdown 的根目录 |
| `NOTE_IMPORT_MAX_FILE_BYTES` | 单个导入文件大小上限 |
| `VITE_API_BASE_URL` | 前端 API 前缀，示例为 `/api` |

## 安装与启动

安装前端依赖：

```powershell
cd frontend
npm.cmd ci
```

终端一启动后端：

```powershell
mvn.cmd spring-boot:run
```

终端二启动前端：

```powershell
cd frontend
npm.cmd run dev
```

Vite 默认把 `/api` 代理到 `http://localhost:8080`。如后端地址不同，启动前设置 `VITE_DEV_API_TARGET`。

## 常用命令

```powershell
# 后端测试、校验和打包
mvn.cmd test
mvn.cmd verify
mvn.cmd package

# 前端开发、构建和预览
cd frontend
npm.cmd run dev
npm.cmd run build
npm.cmd run preview
```

前端目前没有配置 `test`、`lint` 或 `format` 脚本。

仓库包含 Maven Wrapper，Linux/CI 使用 `./mvnw`。当前 `mvnw.cmd` 在 Windows 上无法正常启动，因此本地命令使用已安装的 `mvn.cmd`，详见状态文档。

## 项目文档

- [Agent 开发入口](AGENTS.md)
- [系统架构](ARCHITECTURE.md)
- [当前状态](docs/STATUS.md)
- [CI/CD 与容器部署](docs/ci-cd.md)
- [可选 Nginx 本机代理](nginx/README.md)
