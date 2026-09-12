# Docker 与 GitHub Actions

本文描述可选的容器化运行、CI 和服务器部署流程。本地无 Docker 开发方式以根目录 `README.md` 为准。

项目的交付流程分为两部分：

- CI：提交到 `master` 或发起 Pull Request 时，自动执行后端测试、前端构建和 Docker 镜像构建检查。
- CD：推送 `v*` 版本标签或手动触发工作流时，验证代码、把后端和 Web 镜像发布到 GitHub Container Registry（GHCR），随后自动部署到 VPS。

部署不使用 GitHub Environment，也没有人工确认步骤。两个镜像发布成功后，工作流会通过 SSH 连接服务器并用 `compose.prod.yaml` 更新容器。

当前站点采用单用户模式：没有注册入口，除登录和 CSRF 初始化外，所有 `/api/**` 业务接口都需要站点所有者登录。

## 文件说明

| 文件 | 用途 |
| --- | --- |
| `backend/Dockerfile` | 构建 Spring Boot 后端镜像 |
| `frontend/Dockerfile` | 构建 Vue 静态资源和 Nginx Web 镜像 |
| `frontend/nginx.conf` | 在 Web 容器中托管前端并代理 `/api/` |
| `compose.yaml` | 本地从源码构建并启动完整环境 |
| `compose.prod.yaml` | 在服务器上拉取 GHCR 镜像并启动完整环境 |
| `.github/workflows/ci.yml` | CI 验证 |
| `.github/workflows/release.yml` | GHCR 镜像发布与 VPS 自动部署 |

## 可选的本地 Docker 运行

首次启动前创建环境文件和笔记目录：

```powershell
Copy-Item .env.docker.example .env.docker
New-Item -ItemType Directory -Force notes
```

先生成管理员密码的 BCrypt 散列。下面的命令会交互式要求输入密码，不会把密码留在命令历史中：

```powershell
docker run --rm -it httpd:2.4-alpine htpasswd -nBC 12 owner
```

命令会输出 `owner:$2y$...`。只复制冒号后面的散列，修改 `.env.docker`：

```properties
APP_ADMIN_USERNAME=owner
APP_ADMIN_PASSWORD_HASH='$2y$12$完整散列内容'
SESSION_COOKIE_SECURE=false
```

散列必须使用单引号包住，避免 `$` 被 Docker Compose 当成变量。不要把明文密码填入该字段，也不要提交 `.env.docker`。

继续修改 `.env.docker` 中的数据库密码，然后执行：

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

同时还会发布完整提交 SHA 标签和 `latest` 标签。两个镜像都发布成功后，工作流会自动部署这次的版本。也可以从 GitHub Actions 页面手动运行该工作流，并填写自定义镜像标签；该标签同样会被自动部署。

发布动作使用仓库自带的 `GITHUB_TOKEN`，不需要单独创建推送令牌。如果 GHCR 包保持私有，部署服务器需要使用具备 `read:packages` 权限的 Personal Access Token 登录：

```powershell
docker login ghcr.io
```

## 服务器首次准备

自动部署前，需要在 VPS 上完成一次初始化。服务器需要安装 Docker Engine 和 Docker Compose 插件，并准备 `/opt/workstation`：

```bash
sudo mkdir -p /opt/workstation/notes
sudo chown -R deploy:deploy /opt/workstation
cd /opt/workstation
```

示例中的 `deploy` 是专门用于部署的 Linux 用户。该用户必须能登录 SSH、写入 `/opt/workstation`，并拥有执行 Docker 的权限。不要直接使用 `root` 作为日常部署用户。

在服务器上创建 `/opt/workstation/.env.docker`。可以从仓库的 `.env.docker.example` 复制内容，但不要把正式密码或该文件提交到 Git。

确认 `.env.docker` 中的 `GHCR_NAMESPACE` 是小写 GitHub 用户名或组织名，例如：

```properties
GHCR_NAMESPACE=max-xy-p
SESSION_COOKIE_SECURE=true
```

`IMAGE_TAG` 可以保留示例值；自动部署时工作流会临时传入本次发布的标签，无须登录 VPS 修改。

公网部署必须先为域名配置 HTTPS；`SESSION_COOKIE_SECURE=true` 会让登录 Cookie 只通过 HTTPS 发送。如果暂时只用服务器 IP 的 HTTP 地址测试，需要临时改为 `false`，但不建议把这种方式作为正式部署。

如果 GHCR 镜像是私有的，还需要在 VPS 上以部署用户登录一次。Personal Access Token 只需要读取镜像，因此选择 `read:packages`，并把令牌作为密码输入：

```bash
docker login ghcr.io -u <GitHub用户名>
```

这个登录凭据保存在 VPS，不需要放入 GitHub Actions Secret。

## 数据与安全

- MySQL 数据保存在 `workstation_mysql_data` 命名卷中，更新应用容器不会删除数据。
- `schema.sql` 只在 MySQL 数据卷首次初始化时执行。
- `sample-data.sql` 包含清空表的语句，不会被生产 Compose 自动执行。
- `.env.docker`、笔记目录和数据库密码不会提交到 Git。
- 管理员密码以 BCrypt 散列形式配置，后端不会从配置文件读取明文密码。
- 登录使用服务端 Session；Cookie 为 HttpOnly、SameSite=Lax，并对写操作启用 CSRF 校验。
- `NOTE_IMPORT_ALLOWED_ROOTS` 在容器中固定为 `/data/notes`，页面导入时也应填写该容器路径。

## 自动部署到远程服务器

为部署用户生成一对独立、无口令的 SSH 密钥，把公钥加入 VPS 上该用户的 `~/.ssh/authorized_keys`，把私钥交给 GitHub Actions。工作流是非交互运行的，无法输入私钥口令；因此要限制这把密钥只用于部署，并且不要复用个人日常登录私钥。

在 GitHub 仓库的 `Settings → Secrets and variables → Actions` 中添加以下 Repository secrets：

| Secret | 内容 |
| --- | --- |
| `VPS_HOST` | VPS 的域名或 IP 地址 |
| `VPS_PORT` | SSH 端口；可以不创建，默认使用 `22` |
| `VPS_USER` | 部署用户，例如 `deploy` |
| `VPS_SSH_PRIVATE_KEY` | 与服务器公钥对应的完整私钥 |
| `VPS_SSH_KNOWN_HOSTS` | VPS 的 SSH 主机公钥记录，用于校验服务器身份 |

`VPS_SSH_KNOWN_HOSTS` 可以在可信电脑上生成。下面以默认 SSH 端口为例：

```bash
ssh-keyscan -p 22 -H your.example.com
```

把完整输出保存为 Secret。保存前，应将扫描到的主机密钥指纹与 VPS 控制台显示的指纹核对，避免把错误服务器加入信任列表。使用非默认端口时，要同时替换命令中的端口和 `VPS_PORT`。

配置完成后，发布流程完全自动：

1. 推送 `v*` 标签或从 Actions 页面手动运行发布工作流。
2. CI 测试通过后，同时构建并推送后端、Web 镜像。
3. 两个镜像发布成功后，工作流通过 SSH 上传 `compose.prod.yaml` 和 `schema.sql`。
4. VPS 拉取本次镜像并执行 `docker compose up -d --remove-orphans`，只重建发生变化的容器，数据库命名卷保持不变。
5. 工作流访问 Web 容器中的 `/api/auth/csrf` 做健康检查；失败时输出后端和 Web 日志，并将部署标记为失败。

该流程没有人工确认步骤，也不需要 VPS 重新执行 Maven、npm 或 Docker 镜像构建。数据库密码、管理员密码散列等运行时机密仍只保存在 VPS 的 `/opt/workstation/.env.docker`，不要添加到 GitHub Secrets 或工作流文件中。

需要手动回滚时，可在 VPS 上临时指定旧镜像标签并重新拉取、启动：

```bash
cd /opt/workstation
IMAGE_TAG=v0.1.0 docker compose --env-file .env.docker -f compose.prod.yaml pull
IMAGE_TAG=v0.1.0 docker compose --env-file .env.docker -f compose.prod.yaml up -d --remove-orphans
```
