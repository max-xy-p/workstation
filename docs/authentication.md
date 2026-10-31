# 认证与授权说明

## 为什么需要独立认证体系

Workstation 支持多个用户和机器主体，需要明确区分“谁在访问”“能否进入系统”和“能否访问某个 Workspace”。浏览器登录、机器服务调用、Session、Token 和业务权限如果都放在 Backend 中，会让业务服务同时承担身份系统、会话系统和 API 授权职责，也难以安全地扩展其他客户端。

当前方案将职责拆开：Keycloak 管理身份，Gateway 管理浏览器会话和统一入口，Backend 只判断业务请求携带的身份是否可信并具有权限。

这套结构的目标不是增加登录步骤，而是确保：

- 浏览器不接触 OAuth2 Token 和 Client Secret。
- Python 等机器服务不需要模拟浏览器登录。
- Backend 即使被内部网络直接访问，也仍然保留安全校验。
- 不同客户端可以共享业务 API，但不共享认证状态。

## 核心概念

- **认证**：确认调用方是谁。浏览器由用户登录完成，机器服务由 Client Credentials 完成。
- **系统准入**：Keycloak 系统角色决定主体能否经过 Gateway 和 Backend 进入 Workstation API。
- **业务授权**：Backend 根据 Workspace 成员关系、成员角色和 Permission 决定具体资源操作；Keycloak 系统角色不授予任意 Workspace 内容权限。
- **Session**：Gateway 为浏览器保存的服务端登录状态，浏览器只持有 Session Cookie。
- **Access Token**：Keycloak 签发的短期访问凭证，由 Gateway 或机器服务发送给 Backend。
- **CSRF**：防止其他网站利用浏览器自动携带 Cookie 发起写请求的保护机制。

## 组件职责

| 组件 | 主要职责 | 不负责的内容 |
| --- | --- | --- |
| Keycloak | 用户、密码、登录页面、SSO、Token、系统角色 | Workspace 成员关系、内容权限和业务规则 |
| Mail Service | 邮件任务、Resend 投递、去重重试及签名回调 | 生成验证码、验证账号身份、签发登录 Token |
| Gateway | OAuth2 Client、浏览器 Session、CSRF、退出、Token 转发、系统角色入口校验 | Workspace 成员和资源级授权 |
| Backend | 验证 JWT、检查系统角色、Workspace 成员授权和审计 | 登录页面、浏览器 Session、用户密码 |
| Frontend | 触发登录、携带 Session/CSRF、选择 Workspace、展示服务端返回的权限状态 | 保存 Access Token、处理 Client Secret、替代 Backend 授权 |
| Python 服务 | 使用自己的机器身份申请 Token 并调用 API | 复用用户 Session 或浏览器 Cookie |

## 公开注册

工作台通过单一“登录或注册”入口进入 Keycloak，注册选项统一保留在身份页，普通注册者自动获得 WORKSTATION_USER，完成邮箱验证码验证并首次登录后自动创建其个人 Workspace。系统准入不授予其他空间的成员权限。验证码与验证结果由 Keycloak 管理，独立邮件服务通过 Resend 投递；详见[邮件服务](mail.md)及[公开注册](registration.md)。

## 两类调用方式

| 调用方 | 身份来源 | 浏览器 Session | CSRF | 到 Backend 的凭证 |
| --- | --- | --- | --- | --- |
| 浏览器 | 用户登录 | 使用 | 写请求需要 | Gateway 转发的 Access Token |
| Python 服务 | Client Credentials | 不使用 | 不需要 | 原始 Bearer Token |

两类请求可以调用相同的业务 endpoint，但认证过程互不替代。浏览器不能获得机器 Client Secret，机器服务也不能依赖用户浏览器是否已经登录。

## 两级角色模型

Keycloak Realm Role 只负责系统准入：

| 系统角色 | 用途 |
| --- | --- |
| `WORKSTATION_USER` | 允许浏览器用户进入业务 API |
| `WORKSTATION_ADMIN` | 平台级管理身份；不自动获得 Workspace 内容权限 |
| `WORKSTATION_SERVICE` | 允许 Client Credentials 机器主体进入业务 API |

Workspace 成员角色保存在 Workstation 数据库中：

| 成员角色 | 权限范围 |
| --- | --- |
| `OWNER` | Workspace、成员、内容和审计的完整权限 |
| `ADMIN` | 管理成员、内容和审计，但不能添加、降级或移除 Owner |
| `EDITOR` | 读取 Workspace，并创建、修改、删除和导入内容 |
| `VIEWER` | 只读 Workspace 内容 |

所有业务资源归属于 Workspace。非成员访问目标 Workspace 时按资源不存在处理，避免暴露空间是否存在；成员权限不足时返回 `403`。跨 Workspace 的文件夹、标签、项目等关联由 Backend 拒绝。

成员管理界面按用户名或显示名称模糊查找已进入过本系统的用户，输入后防抖查询，最多返回 10 个候选。只有具备 `MEMBER_MANAGE` 的成员可以查找和添加；添加请求仅提交主体 ID 与角色，Backend 从已记录的安全主体读取类型和展示资料，不接受浏览器提交身份投影。最后一个 `OWNER` 不能被移除或降级。

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

Gateway 在建立登录会话前验证 Access Token 的签名、Issuer、Audience 和有效期，并从其中的 `realm_access.roles` 映射 `WORKSTATION_USER`、`WORKSTATION_ADMIN` 系统准入角色。ID Token 用于确认登录身份，不作为系统角色来源；没有相应角色的用户不能访问业务 API。

## 浏览器 API 流程

读请求携带 Session Cookie。Gateway 从服务端 Session 中取得 Access Token并转发给 Backend。

写请求还必须携带 CSRF Token。CSRF 保护针对的是浏览器自动携带 Cookie 的特性，因此只适用于浏览器 Session 请求。Backend 收到的仍然是 Bearer Token，它不维护浏览器会话。

## 机器服务流程

```text
Python Service
  -> 使用独立 Client ID/Secret 向 Keycloak 申请 Token
  -> 携带 Bearer Token 请求 Gateway
  -> Gateway 验证并保留机器 Token
  -> Backend 再次验证 Token、系统角色和 Workspace 成员权限
```

机器服务没有浏览器 Cookie，因此不需要 CSRF。Gateway 必须先确认 Bearer Token 有效，才能按机器请求处理；不能仅因为请求中出现了 `Authorization` Header 就跳过 CSRF。

每个独立机器服务使用自己的 Keycloak Client，并以该 Service Account 的稳定 `sub` 加入所需 Workspace。这样可以分别授权、审计、轮换和吊销凭证，而不会影响浏览器或其他服务。

## 为什么 Gateway 和 Backend 都要验证

Gateway 的验证保护统一入口，并区分浏览器请求和机器请求；Backend 的验证保护业务资源。两层验证不是重复登录，而是防御边界不同。

如果 Backend 只信任 Gateway 添加的普通 Header，一旦内部网络、路由或 Gateway 配置出现问题，业务接口就可能失去身份校验。Backend 保留 Resource Server 能力后，无效签名、错误 Issuer/Audience、过期 Token、缺少系统角色或缺少 Workspace 权限的请求仍会被拒绝。

## 退出和失效

浏览器退出时，Gateway 清理本地 Session，并通知 Keycloak 结束统一登录会话。Session 或 Token 过期后，Frontend 回到未登录状态；尚未同步的笔记草稿继续保留在 IndexedDB，不能因认证失效而丢失。

机器 Token 到期后，由认证层重新申请。Client Secret 泄露时只轮换对应机器 Client，不应影响浏览器登录。

## 安全边界

- Web/Nginx 和 Gateway 是统一外部入口，客户端不直连 Backend。
- 生产环境使用 HTTPS，Secret 和完整 Token 不进入日志或 Git。
- 浏览器 Gateway Client、Python Client 和其他机器服务不共享 Secret。
- 身份认证成功不代表任意业务操作都被允许，Backend 仍负责业务授权和数据规则。
- Gateway 不缓存 Workspace 成员关系，Frontend 的按钮显示也不是安全校验；Backend 是 Workspace Permission 的唯一执行者。
- 关键写操作通过 Transactional Outbox、XXL-JOB 和 RabbitMQ 异步写入只追加审计事件；权限拒绝采用独立事务尽力记录，审计故障不改变原授权响应。完整故障边界见 [`audit/README.md`](audit/README.md)。审计不记录 Token、Secret、密码或笔记正文。

当前认证能力、待验证事项和已知问题见 [`STATUS.md`](STATUS.md)。

## GitHub Webhook 专用认证

`POST /api/hooks/github/{endpointId}` 使用独立的原文签名认证。Gateway 不读取或转发 Session、Cookie 与 Bearer Token，Backend 对原始请求执行 HMAC-SHA256 验签；浏览器登录或机器 Token 都不能替代签名。该例外只适用于准确的回调路径 POST，普通业务 API 的系统准入、Workspace 权限和浏览器 CSRF 要求不变。

Webhook 配置、投递查询与显式重试仍是受认证的 Workspace 业务 API，仅允许有数据源管理权限的成员。有效签名只允许持久化通知，不能授予任意 Workspace 的业务访问；后台继续核对连接原主体、配置版本、仓库绑定和任务来源关系。密钥轮换与停用取消旧通知作业，详情见 [数据源流程](integrations.md#github-webhook-与指定来源刷新)。
