# OpenAPI 契约维护

`workstation.yaml` 是 Workstation HTTP API 的契约真源。接口变更先修改该文件，再同步 Backend 实现，并重新生成 Python Client 与 TypeScript SDK。

生成 Python Client：

```powershell
$env:UV_CACHE_DIR = "$PWD/.uv-cache"
$env:UV_TOOL_DIR = "$PWD/.uv-tools"
uvx --from openapi-python-client==0.29.1 openapi-python-client generate `
  --path openapi/workstation.yaml `
  --config openapi/python-client-config.yaml `
  --meta uv `
  --output-path clients/python `
  --overwrite
```

生成 TypeScript SDK：

```powershell
cd clients/typescript
npm.cmd ci
npm.cmd run api:generate
npm.cmd run typecheck
npm.cmd run build
npm.cmd test
```

生成器版本必须固定。`clients/python/workstation_client`、`clients/typescript/src/generated/schema.ts` 和 `clients/typescript/dist` 是生成内容，不要手工修改；Python 认证和高级封装放在独立的 `clients/python-auth/workstation_auth` 包中，Vue 的 Cookie/CSRF Transport 放在 `frontend/src/api/client.ts`。

完整流程、职责边界和认证说明见 [OpenAPI 使用说明](../docs/openapi.md)。
