# Project Status

> 核对日期：2026-09-09。功能基线为 `bcccf4f`；核对时未提交变更仅涉及文档整理。

## Current State

项目已经可以作为单用户本地工作台使用。用户登录后可创建和编辑 Markdown 笔记，通过标题、正文、分类和标签查找笔记，也可以维护项目、任务、分类和标签。笔记正文持久化在 MySQL，已有笔记支持 8 秒防抖自动保存，未提交表单会写入浏览器 `localStorage`。

## Completed

- [x] 单管理员登录、Session 认证、退出和 CSRF 防护
- [x] 笔记创建、详情、更新、删除和列表
- [x] Tiptap 所见即所得 Markdown 编辑及基础格式工具栏
- [x] 手动保存、快捷键保存、已有笔记自动保存和本地草稿恢复
- [x] 笔记标题/正文关键词搜索，分类/标签/项目筛选
- [x] 分类和标签 CRUD，笔记多标签关联
- [x] 白名单本机路径的 Markdown 文件/目录导入及结果明细
- [x] 项目 CRUD、状态、详情及关联任务/笔记
- [x] 任务 CRUD、优先级、截止日期和状态看板
- [x] 仪表盘统计和最近项目/笔记
- [x] Maven 后端测试流程、Vite 前端构建和 GitHub Actions CI

## In Progress

- [ ] 未从当前业务代码、TODO 或 Git 差异中确认正在实现的功能；当前未提交工作是项目知识文档整理。

## Next

- [ ] 完善笔记的组织和查找能力。该方向已由项目所有者确认，具体功能范围尚未确定。

## Known Issues

- 笔记、项目和任务列表都没有分页；笔记列表会返回完整 `MEDIUMTEXT` 正文。
- 笔记列表和项目详情逐篇查询标签，存在 N+1 查询。
- 笔记搜索使用 `%keyword%` 的 SQL `LIKE`，没有全文索引、相关度或命中高亮。
- 自动保存会更新完整笔记并删除后重建标签；没有失败重试或多标签页冲突检测。
- 新笔记首次手动保存前不会创建数据库记录，只保存在当前浏览器草稿中。
- 笔记删除是物理删除；没有回收站、版本历史或恢复功能。
- 编辑器和后端没有图片/附件上传能力。
- Markdown 导入读取后端机器路径，不是浏览器文件上传。
- `schema.sql` 只适合初始化，没有 Flyway/Liquibase 等增量迁移机制。
- 前端没有自动化测试、lint 和 format 脚本；后端没有 Note/Task/Import Service 的专项测试。
- `frontend/src/utils/markdown.js` 当前没有调用方；`marked` 和 `dompurify` 只被该文件引用。
- Windows 下 `mvnw.cmd` 当前会在 Wrapper PowerShell 脚本中因空 `Target` 索引而退出；本机需要使用已安装的 `mvn.cmd`。Linux CI 的 `./mvnw` 不受影响。

## Notes

- 2026-09-09 验证：`mvn.cmd test` 通过 14 个后端测试；`npm.cmd run build` 构建成功。
- 前端构建产物主 JavaScript 约 605 kB，Vite 会给出超过 500 kB 的 chunk 警告。
- MySQL `notes.content` 中保存 Markdown，Tiptap JSON 和渲染 HTML 都不是持久化格式。
- `localStorage` 草稿键为 `workstation:note-draft:{id}`；新笔记使用 `workstation:note-draft:new`。它不是服务端缓存或备份。
- 分类名和标签名由数据库唯一约束保证唯一。
- 分类被笔记引用时拒绝删除；标签删除时解除笔记关联；项目存在任务时拒绝删除。
- `sample-data.sql` 会清空六张业务表后写入样例数据，只能在可丢弃数据的环境中执行。
- 当前代码没有可确认的 SaaS、多用户或多租户实现。
