# Keycloak + Gateway/BFF 实施说明

> 本文主要描述浏览器 BFF 链路。Python Client Credentials Bearer 链路已经补充到 Gateway，详见 [OpenAPI 使用说明](openapi.md)。

## 目标架构

```text
Browser
  -> Web/Nginx
      -> Vue 静态资源
      -> /auth/** -> Keycloak
      -> /oauth2/**、/login/oauth2/**、/api/** -> Gateway/BFF
  -> Gateway/BFF -> Bearer Token -> Backend Resource Server -> MySQL
  -> Gateway Session -> Redis
  -> Keycloak -> MySQL(keycloak)
```

Keycloak 是统一身份提供方，负责账号、密码、SSO 会话和 OIDC Token。Gateway 是 OAuth2 Client 和浏览器 BFF，负责 HttpOnly Session、CSRF、登录回调、注销和 Token Relay。Backend 不再处理登录与 Session，只验证 Keycloak JWT 并要求 `OWNER` 角色。

## 已实现任务

- [x] 新增独立 `gateway/` Spring Cloud Gateway WebFlux 工程。
- [x] Gateway 使用 Spring Boot 4.0.7 和 Spring Cloud 2025.1.2。
- [x] Gateway 使用 Authorization Code + PKCE 接入 Keycloak。
- [x] Gateway 使用 Redis 保存服务端 Session。
- [x] 浏览器只持有 `WORKSTATION_SESSION` Cookie，不持有 OAuth2 Token。
- [x] Gateway 提供 `/api/auth/csrf` 和 `/api/auth/me` BFF 接口。
- [x] Gateway 使用原生 POST `/api/auth/logout` 并触发 OIDC Logout。
- [x] Gateway 将 `/api/**` 请求通过 Token Relay 转发给 Backend。
- [x] Backend 删除原表单登录、内存用户、Session、CSRF 和 Auth Controller。
- [x] Backend 改为无状态 JWT Resource Server。
- [x] Backend 校验 JWT Issuer、Audience、签名、有效期和 `OWNER` 角色。
- [x] 新增 Keycloak `workstation` Realm 导入配置。
- [x] Realm 禁止用户注册和密码模式，启用登录防爆破。
- [x] Realm 创建 Gateway confidential client、API Audience Mapper 和 Owner 角色。
- [x] 初始 Owner 密码为临时密码，首次登录强制修改。
- [x] Vue 登录改为跳转 Keycloak，退出使用带 CSRF 的浏览器表单。
- [x] Nginx、Vite、Compose、生产 Compose、CI 和发布流程接入 Gateway/Keycloak。
- [x] 新增 Gateway 与 Backend 安全测试。

## 运行时组件

| 组件 | 默认端口 | 是否公网暴露 | 职责 |
| --- | ---: | --- | --- |
| Web/Nginx | 8088 | 是 | 唯一应用入口、静态资源和反向代理 |
| Gateway | 8081 | 否 | BFF、Session、CSRF、OIDC Client、Token Relay |
| Backend | 8080 | 否 | 业务 API、JWT 校验与业务授权 |
| Keycloak | 8080（本机映射 8090） | 仅经 `/auth` | OIDC、账号与 SSO |
| Redis | 6379 | 仅本机开发映射 | Gateway Session |
| MySQL | 3306 | 否 | 独立的 `workstation` 业务库与 `keycloak` 身份库 |

## 必填配置

复制配置示例：

```powershell
Copy-Item .env.docker.example .env.docker
```

必须替换以下值：

- `MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`
- `KEYCLOAK_ADMIN_USERNAME`、`KEYCLOAK_ADMIN_PASSWORD`
- `KEYCLOAK_DB_USERNAME`、`KEYCLOAK_DB_PASSWORD`
- `KEYCLOAK_CLIENT_SECRET`，建议至少 32 个随机字符
- `REDIS_PASSWORD`
- `WORKSTATION_OWNER_USERNAME`
- `WORKSTATION_OWNER_INITIAL_PASSWORD`

本机完整容器环境使用：

```properties
WORKSTATION_PUBLIC_URL=http://localhost:8088
WORKSTATION_DEV_URL=http://localhost:5173
```

生产环境必须将 `WORKSTATION_PUBLIC_URL` 设置为不带末尾斜杠的 HTTPS 地址，例如 `https://workstation.example.com`。生产 Compose 会强制 Gateway Session Cookie 使用 Secure。

## 启动与首次登录

```powershell
docker compose --env-file .env.docker up -d --build
docker compose --env-file .env.docker ps
```

访问 `http://localhost:8088`，点击“使用统一身份登录”。使用 `WORKSTATION_OWNER_USERNAME` 和 `WORKSTATION_OWNER_INITIAL_PASSWORD` 登录，Keycloak 会要求立即设置新密码。

Realm 只会在 Keycloak 数据库首次启动时导入。以后修改 Realm JSON 不会覆盖数据库中的现有 Realm；应通过 Keycloak Admin Console 或受控的重新导入流程修改。

如果曾经运行过使用 PostgreSQL 的早期 Keycloak 配置，需要先导出 Realm 和用户，再导入新的 MySQL 身份库；全新环境不需要迁移。

Keycloak Admin Console 地址：

```text
http://localhost:8088/auth/admin/
```

管理控制台使用 `KEYCLOAK_ADMIN_USERNAME`，它与 Workstation Owner 是不同账号。

## 本地源码开发

最稳定的方式是用 Compose 运行完整环境。若需要单独调试 Java/Vue 源码，可先启动基础设施，再在宿主机运行应用：

```powershell
docker compose --env-file .env.docker up -d db redis keycloak
```

随后分别启动 Backend、Gateway 和 Vue。Backend 与 Gateway 的本机配置参照 `backend/.env.example` 和 `gateway/.env.example`；启动命令前需把其中变量写入当前终端或 IDE 的运行环境。Vite 默认把 `/api` 和 OAuth2 回调代理到 `8081`，把 `/auth` 代理到 `8090`。

## 安全边界

- 浏览器不得直连 Backend、Redis 或 MySQL。
- Backend 即使被内部绕过 Gateway，也会拒绝无 JWT、伪造 JWT、错误 Issuer/Audience 或缺少 `OWNER` 角色的请求。
- 浏览器写请求在 Gateway 层校验 CSRF；Backend 使用 Bearer Token，因此自身无浏览器 CSRF 状态。
- Client Secret、Redis 密码、数据库密码和初始密码只能存在于运行时配置，不得提交 Git。
- Keycloak Realm 导入文件只包含环境变量占位符，不包含真实 Secret。
- 生产环境必须使用 HTTPS，且反向代理必须正确传递 Host、Proto 和 Port。

## 验证清单

- [ ] 匿名访问 `/api/projects` 返回 JSON `401`。
- [ ] 登录会跳转 Keycloak，成功后返回 Workstation。
- [ ] `/api/auth/me` 返回当前 Keycloak 用户和 Owner 状态。
- [ ] 浏览器中不存在 LocalStorage/IndexedDB Access Token。
- [ ] 缺少 CSRF Token 的写请求在 Gateway 返回 `403`。
- [ ] 合法写请求经过 Token Relay 后被 Backend 接受。
- [ ] 缺少 `OWNER` 角色的 Keycloak 用户不能访问业务 API。
- [ ] 退出同时清除 Gateway Session 并结束 Keycloak 登录状态。
- [ ] Session 过期时 IndexedDB 待同步笔记草稿仍被保留。
- [x] Backend、Gateway、Frontend 构建及测试通过。
- [x] `docker compose config`、生产 Compose 和 Realm JSON 静态校验通过。

## 后续建议

- 为 Gateway 增加基于 IP/账号的登录限流和统一 request ID 日志。
- 使用自动化浏览器测试覆盖完整 OIDC 跳转链路。
- 为 MySQL 中的业务库、Keycloak 身份库和 Keycloak Realm 制定备份恢复策略。
- 多 Gateway 实例部署前验证 Spring Session Redis 的序列化兼容与滚动升级。
- 后续其他应用接入同一 Keycloak Realm 时，为每个应用创建独立 Client，不共享 Client Secret。

## 官方参考

- [Keycloak OpenID Connect](https://www.keycloak.org/securing-apps/oidc-layers)
- [Keycloak Realm 导入](https://www.keycloak.org/server/importExport)
- [Spring Cloud Gateway](https://docs.spring.io/spring-cloud-gateway/reference/)
- [Spring Security OAuth2 Client](https://docs.spring.io/spring-security/reference/reactive/oauth2/index.html)
- [Spring Security Resource Server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
