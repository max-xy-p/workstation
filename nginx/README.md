# Nginx 本机代理配置

Nginx 是浏览器的同源入口，默认监听 `http://localhost:8088`：

- `/` 转发 Vue 开发服务或读取 `frontend/dist`。
- `/api/`、`/oauth2/`、`/login/oauth2/` 转发 Gateway `localhost:8081`。
- `/auth/` 转发 Keycloak `localhost:8090`。
- Backend `localhost:8080` 不应直接暴露给浏览器。

## 开发模式

先启动 MySQL、Redis 和 Keycloak：

```powershell
docker compose --env-file .env.docker up -d db redis keycloak
```

再分别启动 Backend、Gateway 和 Vite：

```powershell
cd backend
mvn.cmd spring-boot:run

cd ../gateway
mvn.cmd spring-boot:run

cd ../frontend
npm.cmd run dev -- --host 127.0.0.1
```

把 `nginx/workstation.dev.conf` 包含到 Nginx 的 `http {}` 中：

```nginx
include D:/dev/project/workstation/nginx/workstation.dev.conf;
```

然后只访问 `http://localhost:8088`。Vite 必须监听 `5173`；若端口变化，需要同步修改代理配置和 Keycloak Client 的回调白名单。

## 构建产物模式

```powershell
cd frontend
npm.cmd run build
```

把 `nginx/workstation.prod.conf` 包含到 Nginx 的 `http {}` 中：

```nginx
include D:/dev/project/workstation/nginx/workstation.prod.conf;
```

配置默认从 `D:/dev/project/workstation/frontend/dist` 读取静态文件。仓库路径变化时应同步修改 `root`。

## 代理要求

OIDC 回调地址依赖准确的外部地址，因此代理必须传递 `Host`、`X-Forwarded-Proto` 和 `X-Forwarded-Port`。生产环境必须使用 HTTPS，并把 `.env.docker` 的 `WORKSTATION_PUBLIC_URL` 设置为真实公网地址且不带末尾斜杠。
