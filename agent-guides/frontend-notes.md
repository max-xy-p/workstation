# Frontend 与笔记规范

- Vue 页面使用 Composition API 与 `<script setup>`。
- 业务 API 统一通过 `frontend/src/api/workstation.ts` 调用。
- 底层 Cookie、CSRF 和错误转换集中在 `frontend/src/api/client.ts`。
- Tiptap 适配集中在 `frontend/src/components/MarkdownEditor.vue`。
- 正文持久化为 Markdown，不保存 HTML。
- IndexedDB 访问集中在 `frontend/src/utils/noteDraftStore.js`。
- IndexedDB 只保存待确认草稿，不能代替 MySQL。
- `notes.version` 是持久化确认凭证。
- 每次持久化更新必须递增 `notes.version`。
- Frontend 只有在校验服务端响应版本后才能清除对应草稿。
- 文件夹通过 `note_folders.parent_id` 自关联。
- 循环、层级和删除约束由 `NoteFolderService` 负责。
- Frontend 不重复实现 Backend 已经承担的数据完整性规则。
- 沿用现有全局样式和页面结构。
- 修改编辑器样式时检查通用选择器是否影响 ProseMirror 节点。
- 不在多个页面分别实现认证、错误转换、CSRF 或草稿持久化逻辑。
- API 类型优先从 TypeScript SDK 获取，不手写重复 DTO。
