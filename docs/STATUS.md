# Project Status

> 核对日期：2026-09-13。

## Current State

项目已可作为单用户本地工作台使用。概览页是统一入口；笔记路由使用不含全局侧栏的独立工作区，由左侧文件夹树和右侧 Tiptap 编辑器组成。笔记正文持久化在 MySQL，待同步草稿保存在浏览器 IndexedDB。

## Completed

- [x] Keycloak 单点登录、Gateway/BFF 服务端 Session、OIDC 全局退出和 CSRF 防护
- [x] Backend 无状态 JWT Resource Server、Issuer/Audience 校验和 `OWNER` 角色授权
- [x] MySQL 分库保存 Workstation 业务数据和 Keycloak 身份数据，Redis 保存 Gateway Session
- [x] 笔记创建、详情、更新、删除和列表
- [x] Tiptap 所见即所得 Markdown 编辑及基础格式工具栏
- [x] 手动保存、快捷键保存、8 秒自动保存和 IndexedDB 草稿恢复
- [x] `notes.version` 持久化凭证及前端递增校验
- [x] 多级文件夹 CRUD、循环校验和非空删除保护
- [x] 左侧文件夹/笔记树与右侧编辑器布局
- [x] 概览统一入口与无全局侧栏的独立笔记工作区
- [x] 笔记标题/正文关键词搜索，文件夹/标签/项目筛选
- [x] 标签 CRUD 和笔记多标签关联
- [x] 白名单本机路径的 Markdown 文件/目录导入及结果明细
- [x] 项目 CRUD、状态、详情及关联任务/笔记
- [x] 任务 CRUD、优先级、截止日期和状态看板
- [x] 仪表盘统计和最近项目/笔记
- [x] Maven 后端测试、Vite 前端构建和 GitHub Actions CI
- [x] 后端源码、Maven 配置和 Dockerfile 统一整理到 `backend/`

## In Progress

- [ ] 无已确认的进行中功能。

## Next

- [ ] 文件夹拖拽排序与移动笔记，尚未确定实现时间
- [ ] 改进笔记查找体验，具体范围尚未确定

## Known Issues

- 笔记、项目和任务列表没有分页；笔记列表会返回完整 `MEDIUMTEXT` 正文。
- 笔记列表和项目详情逐篇查询标签，存在 N+1 查询。
- 笔记搜索使用 `%keyword%` SQL `LIKE`，没有全文索引、相关度或命中高亮。
- 自动保存更新完整笔记并删除后重建标签；版本目前只作持久化凭证，尚未用于乐观锁和多标签页冲突检测。
- 笔记删除是物理删除；没有回收站、版本历史或恢复功能。
- 编辑器和后端没有图片/附件上传能力。
- Markdown 导入读取后端机器路径，不是浏览器文件上传。
- `schema.sql` 只适合初始化，没有 Flyway/Liquibase 等自动迁移机制。
- 前端没有自动化测试、lint 和 format 脚本。
- Windows 下 `mvnw.cmd` 当前无法正常启动，本机需使用已安装的 `mvn.cmd`。

## Notes

- 2026-09-14 验证：移除额外的 `keycloak-db-init` 容器；Keycloak 数据库初始化已并入 MySQL `/docker-entrypoint-initdb.d`，发布流程兼容已有数据卷；开发与生产 Compose 配置静态校验通过。
- 2026-09-13 验证：Backend `mvn verify` 通过 14 项测试，Gateway `mvn verify` 通过 4 项测试，Frontend `npm run build` 成功；开发与生产 Compose 配置和 Keycloak Realm JSON 静态校验通过。
- 当前验证机器未启动 Docker daemon，完整 Keycloak 浏览器登录、Token Relay 和全局退出链路仍需在可运行 Docker 的环境中做一次烟测。
- 2026-09-12 验证：在 `backend/` 执行 `mvn.cmd test` 通过 16 个后端测试；`npm.cmd run build` 构建成功。
- 现有旧数据库需备份后手动执行一次 `backend/src/main/resources/db/migrate-to-note-folders.sql`。
- 由旧版结构创建的现有数据库还需执行一次 `backend/src/main/resources/db/add-note-version.sql`。
- MySQL `notes.content` 保存 Markdown；Tiptap JSON 和渲染 HTML 都不持久化。
- IndexedDB 数据库为 `workstation-cache`，对象仓库为 `note-drafts`；记录按用户和笔记 ID 隔离，包含当前快照、数据库基准快照和更新时间。
- IndexedDB 不是服务端备份；服务端保存成功后会删除对应待同步草稿。
- 同级文件夹名由 Service 校验，标签名由数据库唯一约束保证唯一。
- `sample-data.sql` 会清空六张业务表后写入样例数据，只能在可丢弃数据的环境中执行。
- 当前代码没有可确认的 SaaS、多用户或多租户实现。
