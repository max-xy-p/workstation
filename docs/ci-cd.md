# CI/CD 与部署说明

## CI/CD 解决什么问题

CI 负责回答“这次修改是否仍然满足项目约束”，CD 负责回答“通过验证的产物如何被发布和部署”。两者共享验证结果，但不等同：CI 成功不会自动改变生产环境，只有明确的发布触发才会产生和部署版本化镜像。

## 交付物

Workstation 的应用交付物是五个容器镜像：

- Backend：业务 API 和数据访问
- Gateway：浏览器 BFF、机器 Token 入口和请求转发
- Mail Service：邮件任务、Resend 投递与签名回调
- Keycloak：身份中心及匹配版本的邮件发送扩展
- Web：Frontend 静态资源和统一反向代理入口

MySQL、Redis、RabbitMQ 和 XXL-JOB Admin 使用上游镜像。Keycloak 基于固定版本的上游镜像加入项目邮件扩展，不修改其身份认证核心。

SDK 是开发制品，不是 Workstation 应用容器。它使用独立标签和发布流程，详见 [`sdk-distribution.md`](sdk-distribution.md)。

## CI 验证范围

```text
代码与配置
  -> Backend 测试
  -> Gateway 测试
  -> 邮件服务真实 MySQL 测试与 Keycloak 邮件扩展测试
  -> OpenAPI 合法性、兼容性和生成漂移
  -> Python 认证层测试
  -> TypeScript SDK 类型检查、构建和测试
  -> Frontend 类型检查和构建
  -> SonarQube 扫描 Backend、Gateway、Frontend 和仓库其他源码与配置
  -> 五个应用镜像构建
```

CI 同时覆盖多语言代码和生成产物，是为了避免契约只在某一种语言中更新。镜像构建也属于验证范围，因为本地源码构建成功不代表 Docker 构建上下文和复制路径一定正确。

Python 验证先按锁文件安装第三方依赖，并禁止从第三方源码构建；仓库内的 SDK 和认证层分别构建 wheel，再离线安装到同一虚拟环境并检查依赖关系，最后运行认证层测试。CI 在版本一致性校验通过后读取 Python SDK 的包版本，用它构造两份明确的本地 wheel 路径；升级 SDK 时无需手动修改工作流中的版本号。

任务写接口自 5.0.0 起强制用户所见版本，配套 Frontend、Backend 和外部 SDK 调用方应一起升级；旧写请求返回参数错误，不使用默认版本兼容。数据库复用既有版本列，本轮没有新迁移。具体操作边界见 [任务编辑并发保护](task-concurrency.md)。

公开注册依赖 Realm 的注册开关与普通默认角色。新环境使用仓库模板，已有身份数据库不会重新导入；应备份后先预览注册配置工具的计划，再由管理员应用，不通过重建数据库升级。步骤见 [公开注册](registration.md#已有-realm-升级)。

## SonarQube 分析

仓库根目录的 `sonar-project.properties` 定义单个项目的分析范围。CI 在扫描作业中验证 Backend、Gateway、Mail Service 和 Keycloak 邮件扩展，提供四个 Java 模块的字节码与 JaCoCo 报告；该作业使用 JDK 21，前三个服务仍生成 Java 17 字节码。扫描前另行检查 Backend/Gateway 合并的行与分支覆盖率是否至少为 80%，未达标时失败。Frontend、SDK 与脚本仍参与静态分析，自动生成的 SDK 文件和构建产物不参与分析，各模块测试目录单独分类。分类行为见 [SonarQube Cloud 官方说明](https://docs.sonarsource.com/sonarqube-cloud/managing-your-projects/project-analysis/setting-analysis-scope/excluding-files-based-on-patterns)。扫描作业等待 Quality Gate 结果，云端新代码覆盖率仍由 Quality Gate 独立判断；同仓库 Pull Request、`master` 推送和手动 CI 触发会执行扫描。外部 Fork 的 Pull Request 不接收扫描凭证。

启用时，需要在 SonarQube Cloud 项目的 `Administration > Analysis Method` 中关闭自动分析，并在 GitHub 仓库的 Actions Secret 中配置具有该项目分析权限的 `SONAR_TOKEN`。Token 只保存在凭证系统中，不写入仓库。云端首次完成 CI 分析后，应确认文件列表同时包含 `backend/src/main/java`、`gateway/src/main/java` 和 `frontend/src`，并核对 Quality Gate 与 Java 告警。应用发布与 SDK 发布复用 CI 时，会继承调用方的标签推送或手动触发事件，因此也执行扫描并等待 Quality Gate；失败会阻止发布。两个发布入口只显式传递 `SONAR_TOKEN`，不继承部署或其他用途的凭据。复用工作流将其声明为必需 Secret；直接运行 CI 时，扫描作业也会先检查凭据是否为空。Token 应配置为仓库 Actions Secret，或具有此仓库访问权限的组织 Secret；本机环境变量和未绑定 Environment 的环境 Secret 不会自动提供给这些作业。

## 标签的含义

| 标签 | 含义 | 结果 |
| --- | --- | --- |
| `v*` | Workstation 应用版本 | 发布五个应用镜像并部署 VPS |
| `sdk-v*` | SDK 契约版本 | 发布 SDK 制品，不部署 Workstation |

标签是明确的发布意图，而不是普通提交的别名。已经发布的版本应保持不可变，后续修复使用新版本。

## 应用发布流程

1. 创建应用版本标签。
2. 发布流程复用完整 CI，失败则停止。
3. 构建 Backend、Gateway、Web、Mail Service 和包含邮件扩展的 Keycloak 五个镜像。
4. 镜像以版本和 Commit 标识发布到 GHCR。
5. 部署流程连接 VPS，同步部署描述、完整数据库 SQL 与初始化脚本、Realm 模板和 XXL-JOB 建库文件。
6. VPS 拉取本次版本镜像并更新容器。
7. 检查关键业务表与迁移字段，并通过统一 Web 入口检查 CSRF 与 Keycloak 发现接口，同时检查邮件服务健康状态。
8. 失败时保留诊断信息，不把失败发布伪装成成功。

## 环境边界

- 本地开发可以运行源码或完整 Compose 环境。
- CI 环境只进行验证和制品构建，不保存业务数据。
- GHCR 保存版本化镜像，不保存运行时 Secret。
- VPS 保存生产配置和持久化数据，只拉取已经构建完成的镜像。
- Web/Nginx 是唯一公网入口，Gateway、Backend、数据库、Redis、RabbitMQ、XXL-JOB Admin 和执行器端口不直接暴露公网。
- Web 容器使用非 root 用户监听 8080；Compose 将配置的宿主机入口端口映射到该端口。

这种分离可以避免“在服务器上现场构建”的不可重复问题，也让回滚针对确定的镜像版本进行。

生产文件挂载要求源文件预先存在，不允许缺失时自动创建同名目录。发布流程在启动前检查上传文件的类型；历史失败部署若已留下同名目录，需要先核对其内容并修复路径，再重新发布。CI 会核对生产只读文件挂载与上传清单，避免新增初始化文件后漏传。

## 数据和身份配置

邮件版本新增独立数据库及配套 Keycloak 镜像，升级前需补齐邮件运行凭据、验证 Resend 域名并初始化邮件数据库。已有 Realm 不会被模板覆盖，测试实际发信后再显式启用邮箱验证；详见[邮件服务升级流程](mail.md#新安装与已有部署)。邮件任务及加密密钥需纳入备份，版本回滚不会自动恢复 Realm 策略。验证码版本需先部署配套 Keycloak 镜像，再按[邮件升级流程](mail.md#新安装与已有部署)应用已有 Realm 的验证码开关；不覆盖账号或定制认证流程。

Backend 以 UID 10001 的普通用户运行，镜像预先准备并授权 `/data/applogs/xxl-job/jobhandler`，供执行器写入日志。新建日志卷继承该目录的权限；已有日志卷不会因镜像更新自动调整属主。若旧卷由 root 创建，应先停止 Backend，仅将对应 `xxl_executor_logs` 卷内的目录和日志授权给镜像中的 `workstation` 用户与组，再更新启动；保留日志内容，不通过删除数据卷或让业务服务长期使用 root 规避权限问题。

容器镜像可以替换，数据库和 Keycloak 身份数据不能按同样方式随意重建。全新环境依次由 schema.sql、migrate-project-integrations.sql、migrate-note-revisions.sql、migrate-task-issues.sql、migrate-github-webhooks.sql、migrate-source-confirmation.sql 和 migrate-external-api-limits.sql 创建业务结构，开发与生产 Compose 已配置执行顺序；已有数据卷不会重新执行首次初始化，因此结构变化必须通过明确的迁移完成。

初始化中途失败后，数据目录可能已经存在，后续重启也不会补跑遗漏脚本。应保留数据，在维护窗口确认目标库、备份并核对已完成的结构，再按依赖顺序补执行对应版本的迁移。重新发布只补齐服务器文件，不会自动修复已有库；关键结构检查失败时发布直接报错，不自动执行生产迁移。

Realm 模板用于首次创建身份配置。已有 Realm 的 Client、Audience、角色和用户变化需要受控更新，不能假设重启会覆盖数据库中的现有配置。

任何生产数据或身份配置变更都应先备份，并明确其与应用版本的兼容关系。

升级包含任务依据笔记功能的已有数据库时，在备份后执行 `backend/src/main/resources/db/migrate-task-evidence.sql`，再发布应用。脚本可重复执行，会为 `tasks` 增加可空关联字段、索引和工作空间内外键；全新数据库直接使用 `schema.sql`。旧应用能保留该可空字段运行，但它不知道如何清除依据关联；若需回滚到旧应用并恢复笔记删除操作，应先清理关联或恢复迁移前备份。迁移不由容器重启自动执行。

升级包含笔记回收站功能的已有数据库时，在备份后执行 `backend/src/main/resources/db/migrate-note-trash.sql`。脚本可重复执行，会增加删除时间、删除主体、查询索引和主体外键；全新数据库直接使用 `schema.sql`。字段保持可空，因此旧应用可以读取数据库，但旧版本不知道过滤软删除记录，不应在未恢复迁移前数据库的情况下回滚应用。迁移不由容器重启自动执行。

GitHub Issue 任务关联版本需要在维护窗口备份并执行 `backend/src/main/resources/db/migrate-task-issues.sql`，随后更新配套应用；旧快照须重新同步后才能用于建立来源。新增表与关联不能随镜像回退自动撤销，回退前暂停任务写入和数据源同步，具体边界见 [Issue 与本地任务](integrations.md#github-issue-与本地任务)。

来源变化确认升级需在维护窗口暂停写入及同步、备份后执行 `backend/src/main/resources/db/migrate-source-confirmation.sql`。新应用依赖新增版本和确认基准，不能与旧应用混合写入；迁移不改变已有任务状态，也不伪造历史确认。完整规则见 [来源变化待处理](integrations.md#来源变化待处理)。

Webhook 升级需在备份、暂停写入和同步后执行 `backend/src/main/resources/db/migrate-github-webhooks.sql`，再部署配套版本。回调为独立签名认证入口，不能用扩大普通业务 API 匿名范围替代。端点和 Secret 由管理员在应用和 GitHub 配套配置；回退前先停用回调与相关作业，不能直接让旧应用处理 ISSUE_REFRESH。完整边界见 [Webhook 流程](integrations.md#github-webhook-与指定来源刷新)。

外部 API 共享限流版本需要先暂停所有旧执行器并备份，再执行 `backend/src/main/resources/db/migrate-external-api-limits.sql`，随后部署配套 Backend 与 Redis 连接配置。迁移新增到期执行时间、采集检查点和领取索引；新旧执行器不得混跑。回滚前暂停同步并处理已有延期作业，旧版本不能理解到期时间和恢复检查点。行为边界见 [共享限流与冷却](integrations.md#外部-api-共享限流与冷却)。

## 回滚含义

笔记版本历史升级必须暂停旧应用写入、备份、执行历史迁移，再切换配套应用版本；不能混用新旧 Backend 写入。基线包含活动与回收站笔记，不改变原有版本号。迁移重复执行、回退及重新升级时的历史缺口，按 [笔记历史的升级与回退](note-history.md#升级与回退) 处理。

镜像回滚是让应用重新运行一个已经验证过的旧版本。它不会自动撤销数据库迁移、Realm 变更或外部数据变化。

因此发布前需要判断本次变化是否只涉及可回滚的应用代码，还是同时包含数据结构变化。后一种情况必须准备对应的数据恢复方案，而不能只依赖旧镜像。

## 当前状态

项目已经具备完整 CI、应用镜像发布、GHCR 存储和 VPS 部署流程。尚未完成或需要真实环境验证的事项统一记录在 [`STATUS.md`](STATUS.md)。
