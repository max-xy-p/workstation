# 认证与授权说明

## 为什么需要独立认证体系

Workstation 虽然目前是单用户系统，但仍需要明确区分“谁在访问”和“是否允许访问”。浏览器登录、机器服务调用、Session、Token 和业务权限如果都放在 Backend 中，会让业务服务同时承担身份系统、会话系统和 API 授权职责，也难以安全地扩展其他客户端。

当前方案将职责拆开：Keycloak 管理身份，Gateway 管理浏览器会话和统一入口，Backend 只判断业务请求携带的身份是否可信并具有权限。

这套结构的目标不是增加登录步骤，而是确保：

- 浏览器不接触 OAuth2 Token 和 Client Secret。
- Python 等机器服务不需要模拟浏览器登录。
- Backend 即使被内部网络直接访问，也仍然保留安全校验。
- 不同客户端可以共享业务 API，但不共享认证状态。

## 核心概念

- **认证**：确认调用方是谁。浏览器由用户登录完成，机器服务由 Client Credentials 完成。
- **授权**：确认已经认证的调用方能否访问业务 API。目前主要依据 `OWNER` 角色。
- **Session**：Gateway 为浏览器保存的服务端登录状态，浏览器只持有 Session Cookie。
- **Access Token**：Keycloak 签发的短期访问凭证，由 Gateway 或机器服务发送给 Backend。
- **CSRF**：防止其他网站利用浏览器自动携带 Cookie 发起写请求的保护机制。

## 组件职责

| 组件 | 主要职责 | 不负责的内容 |
| --- | --- | --- |
| Keycloak | 用户、密码、登录页面、SSO、Token、角色 | Workstation 业务数据和业务规则 |
| Gateway | OAuth2 Client、浏览器 Session、CSRF、退出、Token 转发、机器 Token 入口 | 笔记、项目和任务业务 |
| Backend | 验证 JWT、检查角色、执行业务授权 | 登录页面、浏览器 Session、用户密码 |
| Frontend | 触发登录、携带 Session/CSRF、展示认证状态 | 保存 Access Token、处理 Client Secret |
| Python 服务 | 使用自己的机器身份申请 Token 并调用 API | 复用用户 Session 或浏览器 Cookie |

## 两类调用方式

| 调用方 | 身份来源 | 浏览器 Session | CSRF | 到 Backend 的凭证 |
| --- | --- | --- | --- | --- |
| 浏览器 | 用户登录 | 使用 | 写请求需要 | Gateway 转发的 Access Token |
| Python 服务 | Client Credentials | 不使用 | 不需要 | 原始 Bearer Token |

两类请求可以调用相同的业务 endpoint，但认证过程互不替代。浏览器不能获得机器 Client Secret，机器服务也不能依赖用户浏览器是否已经登录。

## 浏览器登录流程

```text
Browser
  -> Gateway 发起登录
  -> Keycloak 验证用户
  -> Gateway 使用授权码换取 Token
  -> Redis 保存 Gateway Session
  -> Browser 获得 HttpOnly Session Cookie
```

采用 Authorization Code + PKCE 的原因，是让 Token 交换发生在 Gateway，而不是暴露给浏览器。登录完成后，浏览器只知道自己拥有一个会话，不直接读取 Access Token 或 Refresh Token。

## 浏览器 API 流程

读请求携带 Session Cookie。Gateway 从服务端 Session 中取得 Access Token并转发给 Backend。

写请求还必须携带 CSRF Token。CSRF 保护针对的是浏览器自动携带 Cookie 的特性，因此只适用于浏览器 Session 请求。Backend 收到的仍然是 Bearer Token，它不维护浏览器会话。

## 机器服务流程

```text
Python Service
  -> 使用独立 Client ID/Secret 向 Keycloak 申请 Token
  -> 携带 Bearer Token 请求 Gateway
  -> Gateway 验证并保留机器 Token
  -> Backend 再次验证 Token 和角色
```

机器服务没有浏览器 Cookie，因此不需要 CSRF。Gateway 必须先确认 Bearer Token 有效，才能按机器请求处理；不能仅因为请求中出现了 `Authorization` Header 就跳过 CSRF。

每个独立机器服务使用自己的 Keycloak Client。这样可以分别审计、轮换和吊销凭证，而不会影响浏览器或其他服务。

## 为什么 Gateway 和 Backend 都要验证

Gateway 的验证保护统一入口，并区分浏览器请求和机器请求；Backend 的验证保护业务资源。两层验证不是重复登录，而是防御边界不同。

如果 Backend 只信任 Gateway 添加的普通 Header，一旦内部网络、路由或 Gateway 配置出现问题，业务接口就可能失去身份校验。Backend 保留 Resource Server 能力后，无效签名、错误 Issuer/Audience、过期 Token 或缺少角色的请求仍会被拒绝。

## 退出和失效

浏览器退出时，Gateway 清理本地 Session，并通知 Keycloak 结束统一登录会话。Session 或 Token 过期后，Frontend 回到未登录状态；尚未同步的笔记草稿继续保留在 IndexedDB，不能因认证失效而丢失。

机器 Token 到期后，由认证层重新申请。Client Secret 泄露时只轮换对应机器 Client，不应影响浏览器登录。

## 安全边界

- Web/Nginx 和 Gateway 是统一外部入口，客户端不直连 Backend。
- 生产环境使用 HTTPS，Secret 和完整 Token 不进入日志或 Git。
- 浏览器 Gateway Client、Python Client 和其他机器服务不共享 Secret。
- 身份认证成功不代表任意业务操作都被允许，Backend 仍负责业务授权和数据规则。

当前认证能力、待验证事项和已知问题见 [`STATUS.md`](STATUS.md)。
