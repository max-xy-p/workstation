# AGENTS.md

## Project Overview

Workstation 是一个单用户个人工作台，包含多级文件夹 Markdown 笔记、项目资料和任务看板。当前默认入口和主要开发重点是笔记模块；后端数据以 MySQL 为准，浏览器 IndexedDB 仅保存尚未获得服务端确认的草稿。

## Tech Stack

- Java 17、Maven 3.9、Spring Boot 4、Spring Security、MyBatis、MySQL
- Vue 3、Vue Router、Vite、Tiptap、Lucide
- Maven Wrapper、npm、JUnit 5、Mockito、MockMvc

## Repository Structure

```text
src/main/java/com/mike/workstation/  Spring Boot 后端
src/main/resources/                  应用配置和数据库脚本
src/test/                            后端测试
frontend/src/                        Vue 页面、组件、路由和 API Client
frontend/                            前端构建及容器配置
.github/workflows/                   CI 和镜像发布流程
nginx/                               可选的本机 Nginx 配置
```

## Important Documentation

- 项目运行与配置：`README.md`
- 整体架构和数据流：`ARCHITECTURE.md`
- 已完成功能、进行中事项和已知问题：`docs/STATUS.md`
- CI/CD 与容器部署流程：`docs/ci-cd.md`
- 可选 Nginx 本机代理流程：`nginx/README.md`

开始开发前先阅读对应文档和相关源码；若文档与代码冲突，以当前代码为准并同步修正文档。

## Development Rules

- 后端沿用 `Controller -> Service -> Mapper` 分层。Controller 接收 HTTP 请求，Service 负责校验与事务，Mapper 使用 MyBatis 注解 SQL。
- 写操作在 Service 中使用 `@Transactional`；关联对象存在性通过 Mapper 查询后校验。
- Java 领域对象使用 Lombok `@Data`，请求/组合响应主要使用 Java `record`。
- API 位于 `/api`，使用 JSON；创建返回 `201`，删除返回 `204`，统一异常由 `GlobalExceptionHandler` 转换。
- 所有业务 API 都受 Session 认证保护；前端写请求通过 `frontend/src/api/client.js` 自动携带 CSRF Token 和 Cookie。
- 前端页面使用 Vue Composition API 与 `<script setup>`，通过 `frontend/src/api/workstation.js` 调用后端。
- Tiptap 适配集中在 `frontend/src/components/MarkdownEditor.vue`；笔记正文保存为 Markdown，不保存 HTML。
- IndexedDB 草稿访问集中在 `frontend/src/utils/noteDraftStore.js`；它只作待同步内容兜底，不能代替 MySQL。
- `notes.version` 是简易持久化凭证；每次笔记更新必须递增，前端只有校验响应版本后才能清除待同步草稿。
- 文件夹以 `note_folders.parent_id` 自关联形成树；层级和删除约束由 `NoteFolderService` 校验。
- 沿用现有全局样式和页面结构；修改编辑器样式时注意通用选择器可能影响 ProseMirror 节点。
- 后端测试沿用 JUnit 5、Mockito 和 MockMvc。行为变化时补充对应 Service 或集成测试。
- 不修改或回退与当前任务无关的未提交变更。

## Common Commands

```powershell
# Backend
mvn.cmd spring-boot:run
mvn.cmd test
mvn.cmd verify
mvn.cmd package

# Frontend
cd frontend
npm.cmd ci
npm.cmd run dev
npm.cmd run build
npm.cmd run preview
```

前端目前没有 `test`、`lint` 或 `format` 命令，不要在文档或交付说明中声称已运行这些检查。
