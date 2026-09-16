# Docker 与 GitHub Actions

## 交付结构

CI 在 `master` 推送和 Pull Request 上执行：

- Backend `mvn verify`
- Gateway `mvn verify`
- OpenAPI 规范校验、不兼容变更检查、Python Client 生成漂移检查和认证适配层测试
- TypeScript SDK 生成漂移检查、类型检查、构建和 Node 测试
- Frontend `npm ci`、`npm run typecheck` 与 `npm run build`
- Backend、Gateway、Web 三个 Docker 镜像构建

发布工作流由 `v*` 标签或手动触发，向 GHCR 发布：

```text
ghcr.io/<namespace>/workstation-backend:<tag>
ghcr.io/<namespace>/workstation-gateway:<tag>
ghcr.io/<namespace>/workstation-web:<tag>
```

SDK 使用独立的 `sdk-v*` 标签。该标签会运行完整 CI、校验版本一致性、构建 Python wheel/sdist 和 npm 包，并把带 SHA-256 校验和的制品上传到 GitHub Release。其他 Python 服务通过 Git Tag 和 `uv.lock` 获取 SDK，在自己的 GitHub Actions 中构建 GHCR 镜像；VPS 不直接下载 SDK。详细流程见 [`sdk-distribution.md`](sdk-distribution.md)。

MySQL、Redis 和 Keycloak 使用官方镜像，不由本仓库重新构建。MySQL 首次创建数据卷时，
会按顺序执行 `01-schema.sql` 和 `02-keycloak.sh`，分别初始化业务表以及 Keycloak 数据库/专用账号。
项目不再使用额外的数据库初始化容器。

Web 镜像的构建上下文是仓库根目录，因为 `frontend` 通过本地 npm 依赖消费 `clients/typescript`。`frontend/Dockerfile` 只复制 TypeScript SDK 和前端所需文件；不要把 Web 构建上下文改回 `frontend/`，否则 Docker 无法解析 `file:../clients/typescript`。

## 本地 Compose

复制并修改配置：

```powershell
Copy-Item .env.docker.example .env.docker
New-Item -ItemType Directory -Force notes
```

必须替换所有 `replace-with-...` 值。重点配置包括：

- `WORKSTATION_PUBLIC_URL`：浏览器访问应用的完整地址，不带末尾斜杠。
- `KEYCLOAK_CLIENT_SECRET`：Gateway confidential client secret。
- `KEYCLOAK_PYTHON_CLIENT_SECRET`：Python Client Credentials confidential client secret，必须与 Gateway Secret 不同。
- `WORKSTATION_OWNER_USERNAME` / `WORKSTATION_OWNER_INITIAL_PASSWORD`：首个业务 Owner；初始密码为临时密码。
- `KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD`：仅用于身份中心管理。
- `KEYCLOAK_DB_USERNAME` / `KEYCLOAK_DB_PASSWORD`、`REDIS_PASSWORD`、MySQL 密码。

启动与检查：

```powershell
docker compose --env-file .env.docker up -d --build
docker compose --env-file .env.docker ps
docker compose --env-file .env.docker logs -f gateway keycloak backend
```

`db` 的健康检查会实际执行 `SELECT 1`，不只检查 MySQL 进程是否存活。
对于已有 `mysql_data` 的环境，MySQL 不会再自动执行 `/docker-entrypoint-initdb.d`。
首次升级到 Keycloak 架构时，必须在现有 MySQL 容器内手动执行一次同一个幂等脚本：

```powershell
docker compose --env-file .env.docker up -d db
docker compose --env-file .env.docker exec -T db bash /docker-entrypoint-initdb.d/02-keycloak.sh
docker compose --env-file .env.docker up -d
```

这些命令不会删除或重建数据卷。这是已有数据卷升级到 Keycloak 架构时的一次性操作；
新数据卷会在 MySQL 首次启动时自动完成初始化。

访问 `http://localhost:8088`。停止但保留数据：

```powershell
docker compose --env-file .env.docker down
```

不要在需要保留数据时执行 `docker compose down -v`，它会删除业务库、身份库和 Session 数据卷。

## 生产服务器准备

服务器需要 Docker Engine、Docker Compose 插件和一个可执行 Docker 的非 root 部署用户。初始化目录：

```bash
sudo mkdir -p /opt/workstation/notes /opt/workstation/keycloak
sudo chown -R deploy:deploy /opt/workstation
```

在 `/opt/workstation/.env.docker` 保存运行时配置，至少设置：

```properties
GHCR_NAMESPACE=<小写 GitHub 用户名或组织名>
WORKSTATION_PUBLIC_URL=https://workstation.example.com
MYSQL_PASSWORD=<secret>
MYSQL_ROOT_PASSWORD=<secret>
KEYCLOAK_ADMIN_USERNAME=<admin>
KEYCLOAK_ADMIN_PASSWORD=<secret>
KEYCLOAK_DB_USERNAME=keycloak
KEYCLOAK_DB_PASSWORD=<secret>
KEYCLOAK_CLIENT_SECRET=<至少 32 个随机字符>
KEYCLOAK_PYTHON_CLIENT_SECRET=<另一个至少 32 个随机字符>
WORKSTATION_OWNER_USERNAME=owner
WORKSTATION_OWNER_INITIAL_PASSWORD=<temporary-secret>
REDIS_PASSWORD=<secret>
```

生产 Compose 只将 Web 入口绑定到宿主机 `127.0.0.1:${APP_PORT:-8088}`，由宿主机 Nginx 对外提供 HTTPS，
不应将该端口直接开放到 VPS 公网。生产 Compose 固定启用 Secure Session Cookie，
所以公网入口必须正确转发 `Host`、`X-Forwarded-Proto` 与 `X-Forwarded-Port`。

Realm JSON 只会在数据库中不存在 `workstation` Realm 时自动导入。后续身份配置变更应使用 Keycloak Admin Console 或受控导入流程，不会靠重启覆盖现有 Realm。

已有 Realm 启用 Python Client Credentials 时，需要按 `keycloak/workstation-realm.json` 手动或通过受控 Admin API 创建 `workstation-python` confidential client，启用 Service Account，为 Access Token 添加 `workstation-api` Audience，并把 `OWNER` Realm Role 授予该 Service Account。不要删除或重建现有 Realm 来应用这一变更。

如果 GHCR 包是私有的，在 VPS 上用仅有 `read:packages` 权限的 Token 登录一次：

```bash
docker login ghcr.io -u <username>
```

## GitHub Actions Secrets

| Secret | 用途 |
| --- | --- |
| `VPS_HOST` | VPS 域名或 IP |
| `VPS_PORT` | SSH 端口，可省略，默认 22 |
| `VPS_USER` | 非 root 部署用户 |
| `VPS_SSH_PRIVATE_KEY` | 无口令、仅用于部署的私钥 |
| `VPS_SSH_KNOWN_HOSTS` | 已人工核验指纹的主机公钥记录 |

发布流程会上传 `compose.prod.yaml`、MySQL schema、Keycloak 数据库初始化脚本和 Realm 模板。
拉取镜像后会用一次 `docker compose up -d --remove-orphans` 创建或更新全部服务；
Keycloak 再通过 Compose 依赖等待 MySQL 健康。部署健康检查通过 Web 入口请求 Gateway 的 `/api/auth/csrf`；
失败时输出 Backend、Gateway、Keycloak、Redis 和 Web 日志。

## 数据与安全

- 同一 MySQL 实例中的 `workstation` 数据库保存业务数据，`keycloak` 数据库保存身份数据；两者使用独立账号。Redis 保存 Gateway Session。
- 浏览器只持有 HttpOnly Session Cookie；OAuth2 Token 仅存在于 Gateway 服务端会话。
- Gateway 对浏览器写请求执行 CSRF 校验；Backend 无状态校验 JWT 与 `OWNER` 角色。
- Python 使用 Client Credentials Bearer Token 经 Gateway 调用 Backend，不使用浏览器 Session 或 CSRF；Gateway 与 Backend 都校验机器 Token。
- `.env.docker`、真实 Secret 和初始密码不得提交到 Git。
- `sample-data.sql` 会清空业务表，生产环境不得执行。
- 应同时备份 MySQL 中的业务库、Keycloak 身份库以及必要的 Realm 配置。

## 回滚

```bash
cd /opt/workstation
IMAGE_TAG=v0.1.0 docker compose --env-file .env.docker -f compose.prod.yaml pull
IMAGE_TAG=v0.1.0 docker compose --env-file .env.docker -f compose.prod.yaml up -d --remove-orphans
```

应用镜像可以按标签回滚；数据库或 Realm 结构发生不兼容变更时，必须配套执行已验证的恢复方案。
