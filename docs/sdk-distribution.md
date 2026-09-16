# SDK 与服务分发说明

## SDK、认证层和镜像的区别

这三者解决不同问题：

- **SDK**：把 OpenAPI 契约变成某种语言可调用的 DTO 和 endpoint。
- **认证层**：帮助机器服务获取和缓存 Access Token，再创建可用的 SDK Client。
- **服务镜像**：包含业务服务代码、SDK、认证层及全部运行依赖，是 VPS 实际部署的产物。

SDK 本身不会运行，也不会主动寻找需要它的服务。哪个服务需要 SDK，由该服务在依赖配置中明确声明。

## 当前为什么使用 Git Tag

当前规模下，单独维护私有 PyPI 和 npm Registry 会增加仓库权限、制品清理和运维成本。Git Tag 已经能够提供可审计、可锁定的源码版本，因此当前采用：

```text
OpenAPI -> 生成 SDK -> sdk-v* Tag -> 消费服务锁定 Tag 和 Commit
```

GitHub Release 同时保存构建后的 Python 和 TypeScript 制品，便于审计和人工下载。常规 Python 服务仍通过锁文件记录 Git Tag 对应的具体 Commit，避免依赖可移动分支。

如果未来服务数量明显增加、需要依赖搜索或精细包权限，可以再迁移到正式的 Python/npm Package Registry；这不会改变 OpenAPI 作为契约真源的设计。

## 为什么服务镜像使用 GHCR

SDK 是构建输入，镜像才是部署产物。消费服务在 CI 中取得 SDK、完成测试并构建镜像，然后把完整镜像发布到 GHCR。VPS 只需要拉取镜像，不需要访问 SDK 仓库或准备 Python 构建环境。

这样可以保证同一个镜像在 CI 和 VPS 上包含完全相同的依赖，也避免在生产服务器上临时解析 SDK 版本。

## SDK 发布流程

1. 修改 OpenAPI、Backend 和相关 SDK 封装。
2. 根据兼容性确定新的语义化版本。
3. 同步所有 SDK 版本并重新生成客户端。
4. 完整 CI 验证契约、生成漂移、测试和构建。
5. 创建与版本一致的 `sdk-v*` 标签。
6. 发布 Python、TypeScript 制品和校验信息。

已经发布并被消费的标签保持不可变。任何内容变化都发布新版本，不能移动原标签。

## Python 服务消费流程

1. 服务声明生成 SDK 和认证层依赖。
2. 两个依赖指向同一个 `sdk-v*` 标签。
3. 锁文件记录实际 Commit，并随服务仓库提交。
4. 服务 CI 安装锁定依赖并运行自身测试。
5. CI 构建包含 SDK 的服务镜像并发布到 GHCR。
6. VPS 拉取固定镜像标签并运行。

服务通过自己的依赖声明表明“我需要这个 SDK”，不需要 Workstation 维护一份中央服务名单，也不存在认证后由 Workstation 主动把 SDK 推送给服务的过程。

## TypeScript SDK 的消费

当前 Frontend 在同一仓库中使用本地 TypeScript SDK。外部 Node 服务可以使用发布制品，但 SDK 仍不绑定认证方式：浏览器由 Gateway Session 适配，机器服务自行获取 Bearer Token 并注入 Transport。

## 四类凭证

| 凭证 | 获取的资源 | 所在阶段 |
| --- | --- | --- |
| SDK 仓库读取凭证 | 私有 Git 仓库中的 SDK 源码 | 构建阶段 |
| GitHub Actions Token | 向 GHCR 发布服务镜像 | 构建阶段 |
| GHCR 只读凭证 | 从 GHCR 拉取私有镜像 | 部署阶段 |
| Keycloak Client Credentials | 调用 Workstation 业务 API | 运行阶段 |

它们虽然都是“先认证再访问资源”，但权限对象完全不同，不能共用。构建 SDK 的凭证不应进入运行镜像，VPS 也不需要 SDK 仓库凭证。

## 版本关系

- OpenAPI、Python SDK、Python 认证层和 TypeScript SDK 版本一致。
- `sdk-v*` 表示 SDK 版本，`v*` 表示 Workstation 应用版本。
- SDK 标签不会触发应用发布，应用标签也不会改变 SDK 版本。
- 消费服务有自己的版本和镜像标签，可以独立于 Workstation 发布。

契约版本判断见 [`openapi.md`](openapi.md)，镜像发布和部署见 [`ci-cd.md`](ci-cd.md)，当前完成情况见 [`STATUS.md`](STATUS.md)。
