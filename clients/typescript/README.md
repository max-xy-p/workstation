# Workstation TypeScript SDK

该包从 `openapi/workstation.yaml` 生成 TypeScript DTO，并提供与认证方式无关的业务 API。Vue 注入自己的 Session/CSRF Transport；Node.js 可以使用内置 Fetch Transport 并注入 Bearer Token。

```powershell
cd clients/typescript
npm.cmd ci
npm.cmd run api:generate
npm.cmd run typecheck
npm.cmd run build
```

`src/generated/schema.ts` 和 `dist/` 是生成内容，不要手工修改。业务接口变更应先修改 OpenAPI，再重新生成和构建本包。

Node.js 示例：

```ts
import { createFetchTransport, createWorkstationApi } from '@workstation/api-client';

const transport = createFetchTransport({
  baseUrl: 'https://workstation.example.com/api/',
  token: async () => process.env.WORKSTATION_ACCESS_TOKEN
});
const api = createWorkstationApi(transport);
const projects = await api.projectApi.list();
```

SDK 不读取 Client Secret，也不负责选择认证流程。Node.js 应在应用层获取 Client Credentials Token，再通过 `token` 或 `TokenProvider` 注入；浏览器不得使用 Client Credentials。
