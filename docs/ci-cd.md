# Docker 与 GitHub Actions

项目的交付流程分为两部分：

- CI：提交到 `master` 或发起 Pull Request 时，自动执行后端测试、前端构建和 Docker 镜像构建检查。
- CD：推送 `v*` 版本标签或手动触发工作流时，验证代码并把后端、Web 镜像发布到 GitHub Container Registry（GHCR）。

CD 默认只发布镜像，不会连接未知服务器。服务器部署通过 `compose.prod.yaml` 完成。

## 文件说明

| 文件 | 用途 |
| --- | --- |
| `Dockerfile` | 构建 Spring Boot 后端镜像 |
| `frontend/Dockerfile` | 构建 Vue 静态资源和 Nginx Web 镜像 |
| `frontend/nginx.conf` | 在 Web 容器中托管前端并代理 `/api/` |
| `compose.yaml` | 本地从源码构建并启动完整环境 |
| `compose.prod.yaml` | 在服务器上拉取 GHCR 镜像并启动完整环境 |
| `.github/workflows/ci.yml` | CI 验证 |
| `.github/workflows/release.yml` | GHCR 镜像发布 |

## 本地 Docker 运行

首次启动前创建环境文件和笔记目录：

```powershell
Copy-Item .env.docker.example .env.docker
New-Item -ItemType Directory -Force notes
```

修改 `.env.docker` 中的数据库密码，然后执行：

```powershell
docker compose --env-file .env.docker up -d --build
```

访问 `http://localhost:8088`。查看状态和日志：

```powershell
docker compose --env-file .env.docker ps
docker compose --env-file .env.docker logs -f backend
```

停止容器但保留数据库数据：

```powershell
docker compose --env-file .env.docker down
```

不要在需要保留数据库时使用 `docker compose down -v`，因为它会删除 MySQL 数据卷。

## 启用 GitHub CI

提交新增文件并推送到 GitHub 后，`CI` 工作流会自动运行。第一次成功后，可以在仓库的分支保护设置中要求以下检查通过：

- `Backend tests`
- `Frontend build`
- `Docker build - backend`
- `Docker build - web`

基础 CI 不需要额外 Secret。

## 发布版本镜像

创建并推送版本标签：

```powershell
git tag v0.1.0
git push origin v0.1.0
```

`Publish container images` 工作流会发布：

```text
ghcr.io/<GitHub用户名或组织>/workstation-backend:v0.1.0
ghcr.io/<GitHub用户名或组织>/workstation-web:v0.1.0
```

同时还会发布完整提交 SHA 标签和 `latest` 标签。也可以从 GitHub Actions 页面手动运行该工作流，并填写自定义镜像标签。

发布动作使用仓库自带的 `GITHUB_TOKEN`，不需要单独创建推送令牌。如果 GHCR 包保持私有，部署服务器需要使用具备 `read:packages` 权限的 Personal Access Token 登录：

```powershell
docker login ghcr.io
```

## 服务器部署

服务器需要安装 Docker，并取得以下文件：

- `compose.prod.yaml`
- `.env.docker`
- `src/main/resources/db/schema.sql`
- 用于导入 Markdown 的 `notes` 目录

确认 `.env.docker` 中的 `GHCR_NAMESPACE` 是小写 GitHub 用户名或组织名，并把 `IMAGE_TAG` 设置为要部署的版本，例如：

```properties
GHCR_NAMESPACE=max-xy-p
IMAGE_TAG=v0.1.0
```

部署或升级：

```powershell
docker compose --env-file .env.docker -f compose.prod.yaml pull
docker compose --env-file .env.docker -f compose.prod.yaml up -d
```

回滚时，把 `IMAGE_TAG` 改回旧版本并再次执行以上两条命令。

## 数据与安全

- MySQL 数据保存在 `workstation_mysql_data` 命名卷中，更新应用容器不会删除数据。
- `schema.sql` 只在 MySQL 数据卷首次初始化时执行。
- `sample-data.sql` 包含清空表的语句，不会被生产 Compose 自动执行。
- `.env.docker`、笔记目录和数据库密码不会提交到 Git。
- `NOTE_IMPORT_ALLOWED_ROOTS` 在容器中固定为 `/data/notes`，页面导入时也应填写该容器路径。

## 自动部署到远程服务器

当前 CD 停止在“发布可部署镜像”这一步。要继续自动部署，需要确定服务器类型和连接方式，再配置 GitHub Environment、服务器地址、SSH 用户及私钥等 Secret。不要把 SSH 私钥或数据库密码直接写入工作流文件。
