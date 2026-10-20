# CI/CD 与部署说明

## CI/CD 解决什么问题

CI 负责回答“这次修改是否仍然满足项目约束”，CD 负责回答“通过验证的产物如何被发布和部署”。两者共享验证结果，但不等同：CI 成功不会自动改变生产环境，只有明确的发布触发才会产生和部署版本化镜像。

## 交付物

Workstation 的应用交付物是三个容器镜像：

- Backend：业务 API 和数据访问
- Gateway：浏览器 BFF、机器 Token 入口和请求转发
- Web：Frontend 静态资源和统一反向代理入口

MySQL、Redis、RabbitMQ、Keycloak 和 XXL-JOB Admin 使用上游镜像，它们属于运行环境，不由本项目重复构建。

SDK 是开发制品，不是 Workstation 应用容器。它使用独立标签和发布流程，详见 [`sdk-distribution.md`](sdk-distribution.md)。

## CI 验证范围

```text
代码与配置
  -> Backend 测试
  -> Gateway 测试
  -> OpenAPI 合法性、兼容性和生成漂移
  -> Python 认证层测试
  -> TypeScript SDK 类型检查、构建和测试
  -> Frontend 类型检查和构建
  -> SonarQube 扫描 Backend、Gateway、Frontend 和仓库其他源码与配置
  -> 三个应用镜像构建
```

CI 同时覆盖多语言代码和生成产物，是为了避免契约只在某一种语言中更新。镜像构建也属于验证范围，因为本地源码构建成功不代表 Docker 构建上下文和复制路径一定正确。

Python 验证先按锁文件安装第三方依赖，并禁止从第三方源码构建；仓库内的 SDK 和认证层分别构建 wheel，再离线安装到同一虚拟环境并检查依赖关系，最后运行认证层测试。CI 在版本一致性校验通过后读取 Python SDK 的包版本，用它构造两份明确的本地 wheel 路径；升级 SDK 时无需手动修改工作流中的版本号。

## SonarQube 分析

仓库根目录的 `sonar-project.properties` 定义单个项目的分析范围。CI 在扫描作业中运行 Backend 和 Gateway 的 Maven 验证，向 Java 分析器提供字节码，并通过 JaCoCo XML 报告提供两个模块的测试覆盖率。扫描前还会检查两个模块合并的 JaCoCo 行与分支覆盖率是否至少为 80%；未达标时该作业失败。扫描作业同时安装 Frontend 与 TypeScript SDK 的依赖。覆盖率指标当前只评估 Backend 和 Gateway，Frontend、SDK 与脚本仍参与静态分析。自动生成的 Python SDK、TypeScript Schema 和构建产物不参与分析；测试目录单独标记为测试代码。扫描作业等待 Quality Gate 结果，云端新代码覆盖率仍由 Quality Gate 独立判断；同仓库 Pull Request、`master` 推送和手动 CI 触发会执行扫描。外部 Fork 的 Pull Request 不接收扫描凭证。

启用时，需要在 SonarQube Cloud 项目的 `Administration > Analysis Method` 中关闭自动分析，并在 GitHub 仓库的 Actions Secret 中配置具有该项目分析权限的 `SONAR_TOKEN`。Token 只保存在凭证系统中，不写入仓库。云端首次完成 CI 分析后，应确认文件列表同时包含 `backend/src/main/java`、`gateway/src/main/java` 和 `frontend/src`，并核对 Quality Gate 与 Java 告警。发布流程复用的 CI 不重复提交一次标签分析。

## 标签的含义

| 标签 | 含义 | 结果 |
| --- | --- | --- |
| `v*` | Workstation 应用版本 | 发布三个应用镜像并部署 VPS |
| `sdk-v*` | SDK 契约版本 | 发布 SDK 制品，不部署 Workstation |

标签是明确的发布意图，而不是普通提交的别名。已经发布的版本应保持不可变，后续修复使用新版本。

## 应用发布流程

1. 创建应用版本标签。
2. 发布流程复用完整 CI，失败则停止。
3. 构建 Backend、Gateway 和 Web 镜像。
4. 镜像以版本和 Commit 标识发布到 GHCR。
5. 部署流程连接 VPS，并同步运行所需的部署描述。
6. VPS 拉取本次版本镜像并更新容器。
7. 通过统一 Web 入口执行健康检查。
8. 失败时保留诊断信息，不把失败发布伪装成成功。

## 环境边界

- 本地开发可以运行源码或完整 Compose 环境。
- CI 环境只进行验证和制品构建，不保存业务数据。
- GHCR 保存版本化镜像，不保存运行时 Secret。
- VPS 保存生产配置和持久化数据，只拉取已经构建完成的镜像。
- Web/Nginx 是唯一公网入口，Gateway、Backend、数据库、Redis、RabbitMQ、XXL-JOB Admin 和执行器端口不直接暴露公网。
- Web 容器使用非 root 用户监听 8080；Compose 将配置的宿主机入口端口映射到该端口。

这种分离可以避免“在服务器上现场构建”的不可重复问题，也让回滚针对确定的镜像版本进行。

## 数据和身份配置

容器镜像可以替换，数据库和 Keycloak 身份数据不能按同样方式随意重建。全新环境依次由 schema.sql、migrate-project-integrations.sql、migrate-note-revisions.sql 和 migrate-task-issues.sql 创建业务结构，开发与生产 Compose 已配置执行顺序；已有数据卷不会重新执行首次初始化，因此结构变化必须通过明确的迁移完成。

Realm 模板用于首次创建身份配置。已有 Realm 的 Client、Audience、角色和用户变化需要受控更新，不能假设重启会覆盖数据库中的现有配置。

任何生产数据或身份配置变更都应先备份，并明确其与应用版本的兼容关系。

升级包含任务依据笔记功能的已有数据库时，在备份后执行 `backend/src/main/resources/db/migrate-task-evidence.sql`，再发布应用。脚本可重复执行，会为 `tasks` 增加可空关联字段、索引和工作空间内外键；全新数据库直接使用 `schema.sql`。旧应用能保留该可空字段运行，但它不知道如何清除依据关联；若需回滚到旧应用并恢复笔记删除操作，应先清理关联或恢复迁移前备份。迁移不由容器重启自动执行。

升级包含笔记回收站功能的已有数据库时，在备份后执行 `backend/src/main/resources/db/migrate-note-trash.sql`。脚本可重复执行，会增加删除时间、删除主体、查询索引和主体外键；全新数据库直接使用 `schema.sql`。字段保持可空，因此旧应用可以读取数据库，但旧版本不知道过滤软删除记录，不应在未恢复迁移前数据库的情况下回滚应用。迁移不由容器重启自动执行。

GitHub Issue 任务关联版本需要在维护窗口备份并执行 `backend/src/main/resources/db/migrate-task-issues.sql`，随后更新配套应用；旧快照须重新同步后才能用于建立来源。新增表与关联不能随镜像回退自动撤销，回退前暂停任务写入和数据源同步，具体边界见 [Issue 与本地任务](integrations.md#github-issue-与本地任务)。

## 回滚含义

笔记版本历史升级必须暂停旧应用写入、备份、执行历史迁移，再切换配套应用版本；不能混用新旧 Backend 写入。基线包含活动与回收站笔记，不改变原有版本号。迁移重复执行、回退及重新升级时的历史缺口，按 [笔记历史的升级与回退](note-history.md#升级与回退) 处理。

镜像回滚是让应用重新运行一个已经验证过的旧版本。它不会自动撤销数据库迁移、Realm 变更或外部数据变化。

因此发布前需要判断本次变化是否只涉及可回滚的应用代码，还是同时包含数据结构变化。后一种情况必须准备对应的数据恢复方案，而不能只依赖旧镜像。

## 当前状态

项目已经具备完整 CI、应用镜像发布、GHCR 存储和 VPS 部署流程。尚未完成或需要真实环境验证的事项统一记录在 [`STATUS.md`](STATUS.md)。
