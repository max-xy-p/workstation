# Architecture

## Overview

Workstation 是一个前后端分离的模块化单体应用。Vue 单页应用通过 `/api` 调用 Spring Boot JSON 接口；Spring Boot 使用 MyBatis 访问 MySQL。开发环境由 Vite 代理 API，容器环境由 Nginx 托管前端并反向代理后端。

```text
Browser -> Vue/Vite -> /api -> Spring MVC -> Service -> MyBatis Mapper -> MySQL
```

系统没有 Redis、消息队列、搜索服务、对象存储或第三方业务 API。

## Main Modules

- **Notes**：笔记 CRUD、多级文件夹、标题/正文搜索、标签/项目筛选和 Markdown 导入。后端入口是 `NoteController` 和 `NoteFolderController`。
- **Taxonomy**：标签 CRUD；标签通过 `note_tag` 与笔记多对多关联。
- **Projects**：项目资料、状态和详情聚合。项目详情同时返回关联任务和笔记。
- **Tasks**：任务 CRUD、项目归属、优先级、截止日期和 `TODO / IN_PROGRESS / DONE` 看板状态。
- **Dashboard**：聚合活跃项目、任务数量以及最近项目/笔记。
- **Authentication**：配置文件中的单一管理员账号、Spring Security Session、CSRF 和前端路由守卫。
- **Frontend shell**：`App.vue` 提供侧栏和页面框架，`router/index.js` 定义路由，`api/client.js` 统一处理 HTTP、认证和错误。

## Data / Request Flow

### 笔记读取与保存

```text
NotesView / NoteEditorView / NoteTreeItem
  -> noteApi / noteFolderApi
  -> NoteController / NoteFolderController
  -> NoteService / NoteFolderService
  -> NoteMapper + NoteFolderMapper + TagMapper
  -> notes / note_folders / note_tag / tags
```

笔记工作区同时加载文件夹和笔记，前端按 `parentId` 组装树。打开笔记时，右侧编辑器加载文件夹、项目、标签和笔记详情，然后检查 IndexedDB 待同步草稿。Tiptap 解析数据库返回的 Markdown；已有笔记停止修改 8 秒后调用 `PUT /api/notes/{id}`。MySQL 更新会递增 `notes.version`，前端确认响应版本有效且递增后才清除对应草稿。

### 登录与写请求

```text
LoginView -> Spring Security /api/auth/login -> JSESSIONID
unsafe API request -> GET /api/auth/csrf -> CSRF header + session cookie -> protected API
```

`frontend/src/auth.js` 缓存当前会话用户；收到 `401` 时 API Client 触发全局事件并跳回登录页。

## Important Components

- `MarkdownEditor.vue`：Tiptap StarterKit、Markdown 转换、基础格式工具栏和撤销/重做。
- `NoteEditorView.vue`：笔记表单、350ms IndexedDB 草稿、8 秒自动保存和草稿恢复。
- `noteDraftStore.js`：封装 `workstation-cache/note-drafts` IndexedDB 对象仓库。
- `NotesView.vue` / `NoteTreeItem.vue`：两栏笔记工作区、递归文件夹树和文件夹管理。
- `NoteFolderService`：校验父节点、同级重名、循环层级与非空删除。
- `NoteImportService`：递归读取白名单目录下的 `.md`/`.markdown` 文件，提取标题并处理同标题策略。
- `SecurityConfig`：单管理员认证、Session 登录/退出、CSRF 和 JSON 安全错误。
- `GlobalExceptionHandler`：将不存在、业务校验和数据库约束错误转换为统一 JSON。
- `schema.sql`：MySQL 初始表结构；应用本身不会自动执行增量迁移。
- `styles.css`：全站样式，包含页面布局和 Tiptap 文档样式。

## Key Data Models

- `Project`：可拥有多个 `Task`，也可被多个 `Note` 关联。
- `Task`：必须属于一个 `Project`。
- `Note`：包含标题、Markdown 正文和单调递增的持久化版本，可选关联一个 `NoteFolder` 和一个 `Project`。
- `NoteFolder`：通过可空 `parentId` 形成多级树；存在子文件夹或笔记时不能删除。
- `Tag`：通过 `note_tag` 与 `Note` 多对多关联；删除标签会先解除关联。

删除项目时，如果存在任务则拒绝删除；如果只有关联笔记，则先清空笔记的项目关联。删除笔记是物理删除。

## External Dependencies

- **MySQL 8**：唯一必需的外部运行服务，保存全部业务数据。
- **本机文件系统**：Markdown 导入依赖后端可访问且位于 `NOTE_IMPORT_ALLOWED_ROOTS` 下的目录。
- **Nginx / Docker**：仅用于可选本机代理和容器部署，不是本地开发必需项。
- **GitHub Actions / GHCR / VPS**：现有工作流在 `master` 和 PR 上执行后端校验、前端构建和镜像构建；版本标签可发布镜像并部署到 VPS。
