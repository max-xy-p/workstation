# 项目状态

> 核对日期：2026-10-06。

## 当前状态

项目以 Workspace 为边界支持多人协作。概览页是统一入口；用户可创建共享 Workspace、切换空间并管理成员。笔记使用独立工作区和服务端版本条件更新；正文持久化在 MySQL，待确认草稿按用户、Workspace 和编辑会话隔离保存在浏览器 IndexedDB。项目任务可关联同项目的依据笔记，统一搜索页查询当前 Workspace 的项目、任务与笔记。

## 已完成

- [x] GitHub/GitLab Token 连接、平台实例允许范围、加密凭证、Workspace 数据源权限与多仓库项目关联
- [x] 仓库发现、幂等批量导入、持久化队列、租约恢复、手动及定时同步和逐项失败重试
- [x] 近期 Issue、PR/MR、默认分支提交及流水线快照，项目详情与概览展示采集范围和状态
- [x] Keycloak 单点登录、Gateway/BFF 服务端 Session、OIDC 全局退出和 CSRF 防护
- [x] Backend 无状态 JWT Resource Server、Issuer/Audience 校验和 `WORKSTATION_*` 系统角色准入
- [x] Workspace、稳定安全主体、成员关系以及 `OWNER`/`ADMIN`/`EDITOR`/`VIEWER` 权限模型
- [x] 所有业务 API、Service、Mapper、Dashboard 和关联校验按 Workspace 隔离
- [x] 集中式 Workspace Permission 授权、跨空间资源隐藏和最后一个 Owner 保护
- [x] 关键写操作与 Workspace 权限拒绝审计，以及按 Workspace 分页筛选查询
- [x] Transactional Outbox、XXL-JOB 调度、RabbitMQ Publisher Confirm、幂等消费、有限重试和死信队列
- [x] Keycloak 用户/管理员/机器系统角色分离，Gateway 只做系统准入，Backend 执行业务授权
- [x] 可搜索的 Workspace 切换器、个人/共享分组、创建入口、键盘与移动端操作，以及成员名称模糊查询和局部管理状态
- [x] Frontend Workspace 路由和上下文，以及按用户、Workspace、笔记与编辑会话隔离的 IndexedDB 草稿键
- [x] OpenAPI、Python SDK、Python 认证层和 TypeScript SDK 升级为 `4.1.0` 成员搜索契约
- [x] MySQL 分库保存 Workstation、Keycloak 和 XXL-JOB 数据，Redis 保存 Gateway Session，RabbitMQ 传输审计消息
- [x] 笔记创建、详情、更新、软删除、回收站恢复、永久删除和列表
- [x] Tiptap 所见即所得 Markdown 编辑及基础格式工具栏
- [x] 手动保存、快捷键保存、8 秒自动保存和 IndexedDB 草稿恢复
- [x] `notes.version` 条件更新、冲突返回、服务端版本对照与草稿恢复
- [x] 多级文件夹 CRUD、循环校验和非空删除保护
- [x] 左侧文件夹/笔记树与右侧编辑器布局
- [x] 概览统一入口与无全局侧栏的独立笔记工作区
- [x] 当前 Workspace 的项目、任务与笔记统一搜索，笔记正文片段和命中高亮
- [x] 标签 CRUD 和笔记多标签关联
- [x] 白名单本机路径的 Markdown 文件/目录导入及结果明细
- [x] 项目 CRUD、状态、详情及关联任务/笔记，任务可关联同项目的依据笔记
- [x] 任务 CRUD、优先级、截止日期和状态看板
- [x] 仪表盘统计和最近项目/笔记
- [x] API Response DTO 与数据库 Entity 分层，笔记摘要/详情分离及请求长度校验
- [x] 项目、任务和笔记统一分页响应，笔记列表标签批量加载
- [x] OpenAPI 3.1 业务 API 契约、Python 生成客户端及 Client Credentials 认证薄层
- [x] 独立 TypeScript/Node SDK、Vue Transport 接入、类型检查和生成漂移检测
- [x] SDK 版本一致性校验、`sdk-v*` 发布制品和外部 Python 服务 GHCR 分发流程
- [x] 文档职责导航、单一流程主文档和过期方案清理
- [x] Gateway 浏览器 Session 与机器 Bearer Token 双认证链路
- [x] API RFC 3339 Offset 时间、统一错误时间戳和未知请求字段拒绝策略
- [x] Maven 后端测试、Vite 前端构建和 GitHub Actions CI
- [x] 后端源码、Maven 配置和 Dockerfile 统一整理到 `backend/`

## 进行中

- [ ] 目标环境备份后执行项目数据源迁移，配置凭证加密密钥和只读平台 Token，验证真实账号导入及浏览器操作；当前仅验证隔离数据库，未迁移部署数据库。
- [ ] 当前源码的 Sonar 分析待服务可用：用户已授权本次文件分析，但连接不支持 Vortex（403）；静态分析及云端 Quality Gate 尚未确认。
- [ ] 在目标环境备份后执行笔记回收站等迁移，并验证双用户成员操作、双标签页笔记冲突、Gateway CSRF 与浏览器回收站流程。

## 后续计划

- [ ] 按实际使用需求扩展 GitHub App/OAuth、Webhook 和外部 Issue 与工作台任务关联；当前仅支持 Token 与单向同步。
- [ ] 文件夹拖拽排序与移动笔记，尚未确定实现时间
- [ ] 根据真实查询量评估中文全文检索；当前仍使用 SQL `LIKE`

## 已知问题

- 数据源执行历史与未绑定候选仓库目前没有自动清理任务；需结合实际使用量确定保留周期。
- 笔记搜索使用 `%keyword%` SQL `LIKE`，没有全文索引或相关度排序；统一搜索页仅对返回的短片段高亮。
- 自动保存仍更新完整笔记并删除后重建标签，但版本条件更新阻止旧页面覆盖新内容。
- 回收站可以恢复误删笔记，但尚未提供正文版本历史与历史版本恢复。
- 编辑器和后端没有图片/附件上传能力。
- Markdown 导入读取后端机器路径，不是浏览器文件上传。
- `schema.sql` 只适合初始化，没有 Flyway/Liquibase 等自动迁移机制。
- 已有数据卷不会自动创建 `audit_outbox`、`audit_events.event_id` 或 XXL-JOB 数据库；需备份后按审计文档执行独立迁移，本次没有执行数据库迁移。
- 现有数据库和已有 Keycloak Realm 不会自动升级，启用 Workspace 版本前必须按升级说明手动处理并备份。
- 旧 `.env.docker` 需将初始账号变量改为 `WORKSTATION_INITIAL_USERNAME` 和 `WORKSTATION_INITIAL_PASSWORD`。
- 成员查找仅覆盖已登录过本系统的用户，不直接查询 Keycloak 全部用户。
- `PUBLISHED` Outbox 目前没有自动清理任务，长期运行前需要确定保留周期并补充清理 Job。
- 前端尚未配置统一的 test、lint 和 format 脚本；工作空间导航有独立 Node 回归测试，已配置 OpenAPI DTO 生成和 TypeScript/Vue 静态类型检查。
- 前端全局样式仍保留多轮页面改版形成的覆盖规则；根级重复选择器已合并，后续改版仍需逐页视觉回归。
- Windows 下 `mvnw.cmd` 当前无法正常启动，本机需使用已安装的 `mvn.cmd`。

## 验证与说明

- 2026-10-06 修复 Python SDK CI 安装失败：SDK 已升级为 4.1.0，但工作流仍引用 3.1.0 wheel。版本一致性校验通过后，工作流读取包版本并导出到后续步骤，自动构造两份明确的 wheel 路径，保留离线安装、禁止依赖安装和源码构建的限制。工作流 YAML 解析、嵌入 Python 版本导出、SDK 版本一致性检查、两个 4.1.0 wheel 构建、隔离虚拟环境离线安装、依赖兼容性检查及 4 项 Python 测试通过。本机使用 Python 3.13.13，GitHub Actions 的 Linux/Python 3.12 完整执行仍需重新运行 CI。Sonar MCP 最近云端分析未查询到该工作流的未解决问题，当前改动的云端分析与 Quality Gate 尚未验证。

- 2026-10-06 优化工作空间切换与成员管理：桌面、移动端和笔记工作区共用可搜索切换器，展示类型与角色，支持键盘操作、新建入口和保持模块的切换；切换时清除原空间资源 ID 和筛选条件。设置页集中管理当前空间与成员，成员候选按用户名或显示名称简单包含匹配，300ms 防抖并忽略过期响应；添加与角色修改仅锁定当前成员行，移除使用独立确认。契约与全部 SDK 升级为 4.1.0，现有路径、参数与响应结构保持兼容。最后一次 Backend 相关修改后，两模块 mvn.cmd clean verify 通过：Backend 151 项、Gateway 16 项，均无失败、错误或跳过；Backend 包含隔离 MySQL 的中文、大小写、部分匹配、字面符号与 10 条上限验证。python scripts/check_java_coverage.py 通过，Backend 84.01%、Gateway 83.40%、综合 83.96%。Frontend 类型检查与生产构建、3 项独立导航测试、TypeScript SDK 类型检查/构建/3 项测试、Python 4 项测试和版本一致性检查通过。使用本机 Edge 与模拟 API 验证桌面和移动端布局、键盘切换、新建空间、成员筛选与操作状态、过期搜索响应、最后一位 Owner 拒绝、切换失败以及笔记页切换；真实登录会话下的完整集成回归尚未执行。Sonar MCP 在最近云端分析中未查询到相关文件的未解决问题；用户已授权本次文件分析，但 CLI 返回 Vortex 403 并跳过源码分析，当前修改的静态分析与云端 Quality Gate 尚未验证。

- 2026-10-02 实现 GitHub/GitLab 项目数据源接入、多仓库关联、幂等导入、持久化同步队列与近期开发情况快照；项目增加 homepageUrl，契约和 SDK 统一升级到 4.0.0，不保留旧客户端缺少新字段的兼容更新行为。最后一次相关修改后，Backend 与 Gateway 的 mvn.cmd clean verify 分别通过 147 项和 16 项测试（无失败、错误或跳过）；Backend 本次还连接隔离 MySQL 执行真实 Mapper 测试。亲自执行 python scripts/check_java_coverage.py，Backend 83.93%、Gateway 83.40%、综合 83.89%，80% 检查通过。Frontend 类型检查与生产构建、TypeScript SDK 类型检查/构建/3 项测试、Python 4 项测试、SDK 版本一致性与重复生成无漂移检查、开发和生产 Compose 配置校验、文档链接及空白检查通过；隔离 MySQL 8.4 验证新建库、旧结构重复迁移、外键与唯一约束、运行租约及自动导入抑制标记，临时容器已清理。Sonar 分析被自动审批拒绝，原因是向分析服务外发源码未获授权，当前静态分析及云端 Quality Gate 未验证；真实平台账号连接、目标数据库迁移、完整浏览器回归与应用镜像构建尚未执行。
- 2026-10-02 整理多数据源项目同步实现方案，明确项目与外部仓库关系、连接授权、数据快照、后台同步、接口页面和分阶段验收范围。本次仅新增方案并更新文档导航与计划，文档本地链接检查与 Git 差异空白检查通过；未实现功能、执行数据库迁移或运行代码验证。
- 2026-09-27 修复本地首次登录停留在 `/login?login=success`：登录页不再保存已失效的 `/notes` 默认回跳地址，路由守卫会忽略无效回跳并拒绝非数字 Workspace ID。现有 MySQL 数据卷缺少任务依据笔记和回收站字段，已先将 `workstation` 数据库备份到本机临时目录，再执行仓库中的两份幂等迁移脚本；字段与索引检查通过，原先报错的 SQL 条件可执行。Frontend 类型检查、生产构建和 Web 镜像重建通过，`/login` 及新静态资源返回 200；浏览器会话下的完整登录回归仍待确认。
- 2026-09-27 SonarQube Cloud 对 `master` 的 14:39 分析只剩 CI 工作流一条 `githubactions:S8544`，Quality Gate 因新代码安全评级未通过；新代码覆盖率为 89.7%，重复行密度为 0%。已将本地 wheel 安装改为明确列出两份版本一致的文件，并禁止依赖安装、源码构建和联网；两份 wheel 构建及临时虚拟环境中的离线安装通过。此次工作流修改尚未经过新的云端分析，Quality Gate 待下次 CI 确认。
- 2026-09-27 针对 SonarQube Cloud 最近一次分析显示的 51 条未解决 Issue，修正回收站页面语义、迁移和样例 SQL 重复字面量、前端根级重复选择器、Python CI 依赖构建方式，以及 Web 容器的非 root 运行；自动生成代码未改动。Backend 89 项与 Gateway 16 项测试的 `mvn.cmd clean verify` 均通过，覆盖率分别为 80.98% 和 83.40%，综合 81.31%，80% 检查通过。Frontend 类型检查及生产构建通过；Python wheel 安装和 4 项认证层测试通过；开发和生产 Compose 配置校验、Web 镜像构建、容器 UID 101 与 HTTP 200 检查通过。隔离 MySQL 8.4 中验证回收站迁移脚本的重复执行和旧结构升级，并执行样例数据脚本；桌面与移动端对照了 CSS 页面。云端尚未分析本地未提交修改，实际 Issue 状态需以提交后的 CI 扫描为准。
- 2026-09-26 完成笔记回收站：删除改为记录操作者与删除时间的软删除，支持分页检索、恢复和永久删除；普通笔记列表、详情、搜索、概览及项目关联均排除已删除笔记。Backend `mvn.cmd clean verify` 通过 89 项测试，Gateway `mvn.cmd clean verify` 通过 16 项测试；Backend、Gateway 和综合覆盖率分别为 80.98%、83.40% 和 81.31%，80% 检查通过。Frontend 类型检查和生产构建、TypeScript SDK 类型检查/构建/2 项测试、Python 认证层 4 项测试及 SDK `3.1.0` 版本一致性检查通过。隔离 MySQL 8.4 中分别验证全新建库和旧结构执行幂等迁移，两条路径的字段、索引与外键断言均通过。本地 Sonar 规则分析未发现问题，但当前连接不支持 Vortex 分析，云端 Quality Gate 尚待提交后的 CI 扫描；目标数据库迁移和浏览器烟测尚未执行。
- 2026-09-26 将 Backend/Gateway 的 80% 覆盖率检查明确为 Agent 交付前的必做项：最后一次相关修改后重新生成两模块报告、执行检查并在最终回复中报告结果。此项仅修改 Agent 指南，未运行新的 Maven 构建；CI 检查仍保留作为提交后的校验。
- 2026-09-25 在代码验证流程中加入 Backend 与 Gateway 合并覆盖率的 80% 检查，并在 CI 的 SonarQube 扫描前执行。脚本读取两模块 JaCoCo XML 的行与分支计数，报告缺失或低于阈值时失败；本地 3 项脚本测试通过，现有报告的合并结果为 80.55%。CI 尚未执行本次变更。
- 2026-09-25 将 SonarQube 覆盖率统计限定为 Backend 和 Gateway，Frontend、SDK 与脚本仍参与静态分析；补充 Workspace 成员角色更新、移除与用户名查找测试。Backend `mvn.cmd clean verify` 通过 84 项测试，JaCoCo 行/分支覆盖率为 82.76%/67.44%；Gateway 沿用同日已通过的 16 项测试及干净构建报告，行/分支覆盖率为 88.24%/66.67%。按两个模块的 JaCoCo 行与分支计数估算，综合覆盖率为 80.55%。云端 SonarQube 尚未重新分析本次修改，当前云端指标仍是旧结果；Quality Gate 的新代码覆盖率需在下次 CI 扫描后确认。
- 2026-09-25 继续补充 Gateway 安全过滤链、Backend 文件夹与标签规则、审计 Outbox 领取与消息发布确认、首页聚合和 Workspace Owner 保护测试。两个模块 `mvn.cmd clean verify` 通过：Backend 81 项，JaCoCo 行/分支覆盖率 81.53%/65.89%；Gateway 16 项，行/分支覆盖率 88.24%/66.67%。两个 Java 模块均在干净构建中达到 80% 行覆盖率；分支覆盖率未达到 80%。本次本地修改尚未进入新的 SonarQube 云端分析。
- 2026-09-25 增加文件导入、JWT 主体解析、项目和任务关联规则以及 Gateway 认证边界测试。Backend `mvn.cmd verify` 通过 63 项测试，JaCoCo 行/分支覆盖率由 56.6%/39.5% 升至 70.1%/58.5%；Gateway `mvn.cmd verify` 通过 13 项测试，行/分支覆盖率由 60.9%/57.4% 升至 62.0%/66.7%。SonarQube MCP 可读取上次云端扫描的问题列表，本次新增测试尚未进入云端分析。
- 2026-09-24 为 Backend 和 Gateway 接入 JaCoCo，`mvn.cmd verify` 分别通过 46 项与 10 项测试，并生成 HTML/XML 报告；本次本机报告的行覆盖率分别为 56.6% 和 60.9%，分支覆盖率分别为 39.5% 和 57.4%。CI 已配置在 SonarQube 扫描作业中执行两个模块的验证并读取 XML 报告；云端扫描尚未运行，覆盖率导入结果待 CI 验证。
- 2026-09-22 协作与项目上下文实现：共享空间和成员界面、笔记版本冲突及本地草稿恢复、任务依据笔记、统一搜索；OpenAPI 和 SDK 升为 `3.0.0`。Backend `mvn.cmd verify`、TypeScript SDK 类型检查/构建/2 项测试、Frontend 类型检查/构建、Python 认证层 4 项测试及 SDK 版本检查通过。SonarQube CLI 仅读取到上次云端扫描的问题列表，本次未提交代码尚无分析结果。新增任务关联迁移脚本尚未在真实 MySQL 上执行；本机 Docker daemon 不可用，浏览器双用户与双标签页烟测未执行。
- 2026-09-22 为 CI 增加仓库级 SonarQube 扫描配置，覆盖 Backend、Gateway、Frontend 和其他源码；Java 分析依赖同一作业编译出的字节码，扫描等待 Quality Gate。CI YAML 解析、两个 Maven 模块的跳过执行但编译测试代码的构建、TypeScript SDK 和 Frontend 的 `npm ci --ignore-scripts` 均通过。配置仍需 SonarQube Cloud 项目关闭自动分析、GitHub Actions 配置 `SONAR_TOKEN`，并由首次 CI 运行验证云端文件索引和规则结果。
- 2026-09-22 Sonar Issue 定向整改：发布 Job 的写权限收敛到 Job 级别，外部 GitHub Action 固定到已核对的提交 SHA；SDK 发布使用认证层锁文件约束 wheel 依赖，Node 安装禁用生命周期脚本；前端补充筛选控件可访问名称，并调整编辑器工具栏的响应式刷新。TypeScript SDK 构建及 2 项测试、Frontend 类型检查和构建、Python 认证层 4 项测试、SDK 版本检查及独立 wheel 安装导入均通过。Web 镜像构建因 Docker Hub 认证服务无法连接而未完成；本地 Sonar 分析返回 403，云端结果尚未包含本次未提交改动。
- 2026-09-22 修复 Gateway `/api/auth/csrf`：从 `ServerWebExchange` 读取安全过滤器提供的 Token，避免 WebFlux 将 `CsrfToken` 接口当作模型对象构造；HTTP 回归测试先复现 500，修复后 Gateway `mvn.cmd verify` 通过 10 项测试。尚未重建容器或执行浏览器退出烟测。
- 2026-09-21 Gateway 浏览器登录系统角色改为从已验证的 Access Token 读取，不再依赖 ID Token 携带 Realm Role；`mvn.cmd verify` 通过 10 项 Gateway 测试。尚未重新部署容器或执行真实 Keycloak 浏览器登录烟测。
- 2026-09-19 审计异步化：成功业务事件改为 Transactional Outbox，XXL-JOB 调度 Relay，RabbitMQ 负责可靠传输、消费重试和死信；权限拒绝审计失败不再改变 `403/404`。Backend 39 项测试、开发与生产 Compose 配置静态校验通过；未执行数据库迁移和完整容器烟测。
- 2026-09-19 Workspace 改造：Backend、Gateway、OpenAPI 2.0.0、Python/TypeScript SDK 和 Frontend 已切换到 `/api/workspaces/{workspaceId}`；Backend 35 项测试、Gateway 7 项测试、TypeScript SDK 2 项测试、Python 认证层与生成契约 4 项测试、SDK 版本一致性检查、Frontend 类型检查和生产构建通过。
- 2026-09-16 文档整理：认证、OpenAPI、SDK 分发和应用部署分别确定唯一流程文档；删除三个已完成的实施方案，将开发规范、生成限制和注意事项集中到 `AGENTS.md`。
- 2026-09-16 验证：SDK 版本一致性检查通过；`workstation-api-client` 和 `workstation-client-credentials` 均成功构建 wheel 与 sdist，并在隔离虚拟环境中共同安装、导入成功。
- 2026-09-15 验证：使用 `openapi-typescript 7.13.0` 建立独立的 `@workstation/api-client` SDK，包含 DTO、业务 endpoint、分页和 Fetch Transport；SDK 类型检查、构建及 2 项 Node 测试通过；Vue 通过本地 npm 包注入 Session/CSRF Transport，前端类型检查和构建通过。
- 2026-09-15 验证：OpenAPI Python Client 使用 `openapi-python-client 0.29.1` 成功生成；Python Client Credentials 与生成契约共 4 项测试通过；Backend `mvn.cmd verify` 通过 29 项测试，Gateway `mvn.cmd verify` 通过 7 项测试，Frontend `npm.cmd run build` 成功，开发与生产 Compose 配置及 Keycloak Realm JSON 静态校验通过。
- 已有 Keycloak 数据库不会因重启自动更新 Realm；需按 `keycloak/workstation-realm.json` 增加 `WORKSTATION_USER`、`WORKSTATION_ADMIN`、`WORKSTATION_SERVICE`，并分别分配给浏览器用户和 `workstation-python` Service Account。
- 2026-09-15 验证：项目、任务和笔记列表统一为 `PageResponse`（页码从 0 开始、默认 20、最大 100）；笔记树、任务看板和项目选项通过分页汇总保持完整数据；笔记列表、Dashboard 和项目详情的标签改为按笔记 ID 批量加载；Backend `mvn.cmd verify` 通过 27 项测试，Frontend `npm.cmd run build` 成功。
- 2026-09-14 验证：完成数据模型优化第一批，所有业务 Controller 改用 Response DTO；笔记列表、Dashboard 和项目详情中的笔记使用无正文摘要；项目列表不再读取 `run_guide`；Backend `mvn.cmd verify` 通过 21 项测试，Frontend `npm.cmd run build` 成功。
- 2026-09-14 验证：移除额外的 `keycloak-db-init` 容器；Keycloak 数据库初始化已并入 MySQL `/docker-entrypoint-initdb.d`；已有数据卷需手动执行一次幂等脚本；发布恢复为单次全量 `compose up`，并增加失败状态/日志输出；开发与生产 Compose 配置静态校验通过。
- 2026-09-13 验证：Backend `mvn verify` 通过 14 项测试，Gateway `mvn verify` 通过 4 项测试，Frontend `npm run build` 成功；开发与生产 Compose 配置和 Keycloak Realm JSON 静态校验通过。
- 当前验证机器未启动 Docker daemon，完整 Keycloak 浏览器登录、Token Relay 和全局退出链路仍需在可运行 Docker 的环境中做一次烟测。
- 2026-09-12 验证：在 `backend/` 执行 `mvn.cmd test` 通过 16 个后端测试；`npm.cmd run build` 构建成功。
- 由旧版结构创建且已经具备文件夹和笔记版本字段的数据库，需先确认 Keycloak 用户真实 `sub` 并备份，再手动执行 `backend/src/main/resources/db/migrate-to-workspaces.sql`；本次没有引入自动迁移框架。
- MySQL `notes.content` 保存 Markdown；Tiptap JSON 和渲染 HTML 都不持久化。
- IndexedDB 数据库为 `workstation-cache`，对象仓库为 `note-drafts`；记录按用户、Workspace 和笔记 ID 隔离，包含当前快照、数据库基准快照和更新时间。
- IndexedDB 不是服务端备份；服务端保存成功后会删除对应待同步草稿。
- 同级文件夹名由 Service 校验，标签名由数据库唯一约束保证唯一。
- `sample-data.sql` 会清空业务内容和审计事件后写入样例数据，只能在可丢弃数据的环境中执行；执行前需先登录一次创建个人 Workspace。
- 当前实现支持同一部署内的多用户和 Workspace 成员共享，但不包含计费、组织管理、自助注册等 SaaS 能力。
