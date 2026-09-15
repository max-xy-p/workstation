# Workstation Python Client Credentials 认证层

这是 Python 生成客户端之外的认证薄层，负责从 Keycloak 获取并缓存 Client Credentials Access Token，然后创建 `workstation_client.AuthenticatedClient`。

认证并不是 Python SDK 独有的要求。浏览器由 `frontend/src/api/client.ts` 使用 Gateway Session Cookie 和 CSRF；Python 没有浏览器会话，所以通过本包获取 Bearer Token。生成目录 `clients/python/workstation_client` 只负责 HTTP、DTO 和序列化，不读取 Client Secret。

需要的环境变量示例：

```text
WORKSTATION_BASE_URL=https://workstation.example.com
WORKSTATION_TOKEN_URL=https://workstation.example.com/auth/realms/workstation/protocol/openid-connect/token
WORKSTATION_CLIENT_ID=workstation-python
WORKSTATION_CLIENT_SECRET=<secret>
```

调用示例：

```python
import os

from workstation_auth import ClientCredentialsProvider
from workstation_client.api.projects import list_projects

credentials = ClientCredentialsProvider(
    token_url=os.environ["WORKSTATION_TOKEN_URL"],
    client_id=os.environ.get("WORKSTATION_CLIENT_ID", "workstation-python"),
    client_secret=os.environ["WORKSTATION_CLIENT_SECRET"],
)

with credentials.create_client(os.environ["WORKSTATION_BASE_URL"]) as client:
    response = list_projects.sync_detailed(client=client)
    print(response.parsed)
```

使用约束：

- `WORKSTATION_BASE_URL` 指向 Gateway 根地址，不要绕过 Gateway 直连 Backend。
- Client Secret 只能从环境变量或密钥服务读取，不能写进源码、OpenAPI、Git 或日志。
- `ClientCredentialsProvider` 会缓存 Token，并在到期前提前刷新。
- 同步业务使用 `create_client`，异步业务使用 `create_client_async`。
- 不要在业务代码中重新定义 OpenAPI Request/Response 数据类。
