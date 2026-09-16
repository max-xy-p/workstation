# Workstation 客户端说明

所有客户端共同以 `openapi/workstation.yaml` 为 HTTP 契约真源，但按语言和运行环境生成各自的 SDK 与认证适配。

| 目录 | 作用 | 认证位置 |
| --- | --- | --- |
| `python/` | OpenAPI 自动生成的 Python DTO 和 endpoint | 不负责申请 Token |
| `python-auth/` | Python 机器调用的认证薄层 | Keycloak Client Credentials |
| `typescript/` | 独立 TypeScript/Node SDK | 由调用方注入 Transport 或 Bearer Token |
| `../frontend/src/api/` | Vue 对 TypeScript SDK 的浏览器适配 | Gateway Session Cookie 和 CSRF |

## Python 目录边界

`python/workstation_client`、`python/README.md` 和 `python/pyproject.toml` 由 `openapi-python-client` 生成。生成器模板中包含英文固定说明和函数结构，禁止为了翻译而直接修改，否则重新生成时会被覆盖，并导致 CI 漂移检查失败。

项目维护的中文 Python 使用说明位于：

- `python-auth/README.md`：认证配置和调用示例。
- `../docs/openapi.md`：契约、生成流程和安全边界。
- 本文件：不同客户端及认证方式的关系。

手写 Python 代码的模块说明、文档字符串、必要注释和项目错误消息使用中文；类名、函数名、协议字段名以及第三方 API 名称保留其代码约定。

## 远程服务使用

当前仓库内部继续使用本地路径依赖。其他仓库中的 Python 服务通过不可变的 `sdk-v*` Git Tag 和 `uv.lock` 获取 SDK，在 GitHub Actions 中构建为 GHCR 镜像；VPS 只拉取镜像。完整流程见 [`../docs/sdk-distribution.md`](../docs/sdk-distribution.md)。
