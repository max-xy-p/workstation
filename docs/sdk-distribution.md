# SDK 分发与 Python 服务部署

## 1. 当前方案

Workstation 不运行私有 PyPI 或 npm Registry。SDK 与服务镜像采用两条相互独立的分发链路：

```text
SDK 源码：Git Tag -> uv Git 依赖 -> 消费服务的构建流程
服务程序：GitHub Actions -> GHCR 容器镜像 -> VPS
```

SDK 只在开发和镜像构建阶段使用。VPS 不下载 SDK 源码，只拉取已经包含全部依赖的 GHCR 镜像。

## 2. SDK 版本和标签

以下位置必须使用同一个语义化版本：

- `openapi/workstation.yaml` 的 `info.version`
- `openapi/python-client-config.yaml` 的 `package_version_override`
- `clients/python/pyproject.toml`
- `clients/python-auth/pyproject.toml`
- `clients/typescript/package.json`

使用下面的命令检查：

```powershell
python scripts/check_sdk_versions.py
python scripts/check_sdk_versions.py --tag sdk-v1.0.0
```

SDK 标签格式固定为 `sdk-v<版本>`，例如：

```powershell
git tag -a sdk-v1.0.0 -m "发布 Workstation SDK 1.0.0"
git push origin sdk-v1.0.0
```

不要移动或覆盖已经发布的 SDK 标签。内容变化时必须发布新版本。

## 3. SDK 发布工作流

`.github/workflows/sdk-release.yml` 在推送 `sdk-v*` 标签时执行：

1. 运行仓库完整 CI。
2. 校验标签与全部 SDK 版本一致。
3. 构建 `workstation-api-client` 的 wheel 和 sdist。
4. 构建 `workstation-client-credentials` 的 wheel 和 sdist。
5. 在隔离环境中安装两个 wheel 并验证导入。
6. 构建和测试 `@workstation/api-client` npm 包。
7. 计算全部制品的 SHA-256。
8. 创建 GitHub Release 并上传制品。

GitHub Release 是审计和手工下载入口。Python 服务的常规依赖仍锁定 Git Tag 和 Commit，不依赖 Release 下载地址。

应用发布使用 `v*` 标签，SDK 发布使用 `sdk-v*` 标签，两者不会互相触发：

```text
v1.2.0      -> 发布 Workstation Backend/Gateway/Web 镜像并部署 VPS
sdk-v1.1.0  -> 发布 SDK 源码版本和 GitHub Release 制品
```

## 4. 新 Python 服务引用 SDK

新服务的 `pyproject.toml` 同时声明生成客户端和认证层。将仓库地址替换为实际地址：

```toml
[project]
name = "workstation-sync-service"
version = "0.1.0"
requires-python = ">=3.11"
dependencies = [
    "workstation-api-client==1.0.0",
    "workstation-client-credentials==1.0.0",
]

[tool.uv.sources]
workstation-api-client = {
    git = "https://github.com/<owner>/workstation.git",
    tag = "sdk-v1.0.0",
    subdirectory = "clients/python"
}
workstation-client-credentials = {
    git = "https://github.com/<owner>/workstation.git",
    tag = "sdk-v1.0.0",
    subdirectory = "clients/python-auth"
}
```

随后生成并提交锁文件：

```powershell
uv lock
uv sync --locked
```

`uv.lock` 会记录标签解析出的 Commit。不要依赖 `master` 分支，也不要在依赖 URL 中写入 GitHub Token。

## 5. 私有仓库读取凭证

如果 Workstation 是私有仓库，在消费服务仓库中创建 `WORKSTATION_SDK_PAT` Secret。它应使用只读的 Fine-grained PAT，并且只授予 Workstation 仓库 `Contents: Read` 权限。

普通 GitHub Actions 步骤可以这样配置 Git Credential Helper：

```yaml
- name: 配置 SDK 仓库读取凭证
  env:
    GH_TOKEN: ${{ secrets.WORKSTATION_SDK_PAT }}
  run: |
    printf '%s' "$GH_TOKEN" | gh auth login --with-token
    gh auth setup-git

- name: 安装锁定依赖
  run: uv sync --locked
```

凭证不能写入 `pyproject.toml`、`uv.lock`、Dockerfile、镜像层或构建日志。

## 6. Python 服务 Dockerfile

下面的模板使用 BuildKit Secret，只在执行 `uv sync` 的构建步骤中临时提供 GitHub Token：

```dockerfile
# syntax=docker/dockerfile:1.7
FROM python:3.12-slim AS builder

COPY --from=ghcr.io/astral-sh/uv:0.11.28 /uv /uvx /bin/
WORKDIR /app
COPY . .

RUN --mount=type=secret,id=github_token \
    token="$(cat /run/secrets/github_token)" && \
    git config --global url."https://x-access-token:${token}@github.com/".insteadOf "https://github.com/" && \
    uv sync --locked --no-dev --no-editable && \
    rm -f ~/.gitconfig

FROM python:3.12-slim
WORKDIR /app
COPY --from=builder /app /app
ENV PATH="/app/.venv/bin:${PATH}"
CMD ["python", "-m", "workstation_sync_service"]
```

公开仓库可以删除 Secret 挂载和 Git 凭证配置，只保留 `uv sync`。

## 7. 消费服务发布到 GHCR

新服务可以使用下面的 GitHub Actions 工作流：

```yaml
name: 发布服务镜像

on:
  push:
    tags:
      - "v*"

permissions:
  contents: read
  packages: write

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6

      - uses: docker/setup-buildx-action@v4

      - name: 登录 GHCR
        uses: docker/login-action@v4
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: 生成镜像元数据
        id: metadata
        uses: docker/metadata-action@v6
        with:
          images: ghcr.io/${{ github.repository_owner }}/workstation-sync-service
          tags: |
            type=ref,event=tag
            type=sha,format=long

      - name: 构建并发布镜像
        uses: docker/build-push-action@v7
        with:
          context: .
          push: true
          tags: ${{ steps.metadata.outputs.tags }}
          labels: ${{ steps.metadata.outputs.labels }}
          secrets: |
            github_token=${{ secrets.WORKSTATION_SDK_PAT }}
```

`WORKSTATION_SDK_PAT` 只用于构建阶段读取 SDK 源码；服务运行时使用的是独立的 Keycloak Client ID 和 Secret。

## 8. VPS 部署

VPS 只需要登录 GHCR 并运行服务镜像。公开镜像可以直接拉取；私有镜像需要创建仅含 `read:packages` 权限的 GitHub Personal Access Token (classic)，登录时把 Token 通过标准输入传入，不能把它写入 Compose 文件：

```bash
printf '%s' "$GHCR_READ_TOKEN" | docker login ghcr.io -u <github-user> --password-stdin
docker compose pull
docker compose up -d --remove-orphans
```

生产 Compose 应固定到服务发布标签，不使用 `latest`：

```yaml
services:
  workstation-sync-service:
    image: ghcr.io/<owner>/workstation-sync-service:v0.1.0
    restart: unless-stopped
```

服务运行时配置示例：

```properties
WORKSTATION_BASE_URL=https://workstation.example.com
WORKSTATION_TOKEN_URL=https://workstation.example.com/auth/realms/workstation/protocol/openid-connect/token
WORKSTATION_CLIENT_ID=workstation-sync-service
WORKSTATION_CLIENT_SECRET=<独立密钥>
```

每个机器服务应使用独立 Keycloak Client，以便单独审计、轮换和吊销凭证。SDK 版本可以共享，运行时身份不能共享。
