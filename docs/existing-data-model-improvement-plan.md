# Workstation 现有功能数据模型完善方案

> 状态：第一批“纯数据模型边界”和第二批列表分页、批量标签加载均已实施
> 编写日期：2026-09-14  
> 范围：仅完善 Workstation 当前已有功能的数据模型与接口契约

## 1. 目标

本方案只整理和完善 Workstation 已经具备的业务能力：

- Dashboard 概览。
- 项目管理。
- 任务管理与任务看板。
- Markdown 笔记管理和搜索。
- 多级笔记文件夹。
- 标签管理。
- Markdown 文件导入。

本阶段的目标是让数据库实体、业务请求和 API 响应各自具有明确边界，为 Web 和 MCP/Agent 提供一致、稳定的数据含义。

本阶段不新增业务功能，不设计以下内容：

- Agent 专用聚合接口。
- 新的搜索能力。
- 操作审计。
- 幂等请求。
- 回收站或软删除。
- Agent 写权限。
- 新的任务提醒或统计指标。
- MCP Tool 实现。

## 2. 当前问题

### 2.1 领域对象直接作为 API 响应

当前 Controller 直接返回 `Note`、`Project`、`Task`、`Tag` 和 `NoteFolder`。这些类型同时承担数据库映射和外部接口契约两种职责。

影响：

- 数据库字段变化可能无意改变 Web 和 Agent 接口。
- 无法明确区分列表、详情和关联对象需要的字段。
- 容易把大字段或内部字段返回给不需要它们的调用方。
- Controller 的响应结构缺少独立契约测试。

### 2.2 笔记列表返回完整正文

`Note` 包含 `content`，而 `GET /api/notes`、Dashboard 最近笔记和项目详情中的关联笔记都会使用 `Note`。

影响：

- 列表查询读取 `MEDIUMTEXT` 正文。
- Web 的笔记树、Dashboard 和项目详情并不使用这些正文。
- 数据量增大后会增加数据库读取、JSON 序列化和网络开销。
- MCP Server 若直接调用现有接口，只能在 Python 中再次过滤数据。

### 2.3 列表接口没有统一容器

项目、任务和笔记列表直接返回 JSON 数组，没有统一的总数、分页和排序描述。

影响：

- Web 无法逐页加载。
- MCP/Agent 无法可靠判断是否还有结果。
- 不同列表未来可能形成不同分页格式。

### 2.4 请求模型复用范围过大

当前创建和更新共用同一个 Request，例如 `NoteRequest`、`ProjectRequest` 和 `TaskRequest`。

目前字段大体相同，因此不要求立即拆分；但需要明确这些模型代表“完整替换请求”，避免调用方误以为未提供的字段会保持不变。

### 2.5 Java 校验与数据库约束没有完整对齐

数据库已经限制标题、名称和 URL 等字段长度，但 Service 层只有部分字段进行了显式长度校验。

影响：

- 超长输入可能最终表现为数据库约束异常。
- Web 和 Agent 得到的错误不够具体。
- 同一类字段的校验行为不一致。

### 2.6 关联数据返回结构不明确

`ProjectDetailResponse` 直接包含 `List<Task>` 和 `List<Note>`；Dashboard 直接包含 `List<Project>` 和 `List<Note>`。

影响：

- 聚合接口可能携带完整笔记正文。
- 关联对象未来增加字段时，聚合接口也会被动膨胀。
- Web 实际只使用其中少量字段。

## 3. 数据模型分层

建议将模型分为四类。

### 3.1 Entity：数据库持久化模型

保留现有：

```text
Note
NoteFolder
Project
Task
Tag
```

Entity 仅用于：

- MyBatis Mapper 输入输出。
- Service 内部业务处理。
- 表达数据库记录和关联查询结果。

Entity 不再直接作为 Controller 返回值。

### 3.2 Request：业务请求模型

保留并完善：

```text
NoteRequest
NoteFolderRequest
ProjectRequest
ProjectStatusRequest
TaskRequest
TaskStatusRequest
TagRequest
NoteImportRequest
```

Request 负责：

- 描述客户端允许提交的字段。
- 承载基础格式和长度校验。
- 区分完整更新、状态更新和导入等不同操作。

### 3.3 Response：外部响应模型

新增独立响应 DTO：

```text
NoteSummaryResponse
NoteDetailResponse
NoteFolderResponse
ProjectSummaryResponse
ProjectDetailResponse
TaskResponse
TagResponse
DashboardResponse
PageResponse<T>
```

Response 负责：

- 为 Web 和 MCP/Agent 提供稳定接口契约。
- 明确列表与详情的数据边界。
- 隔离数据库实体变化。

### 3.4 Enum：稳定业务枚举

继续使用：

```text
ProjectStatus
TaskStatus
TaskPriority
NoteImportDuplicateStrategy
```

枚举值保持不变，避免影响现有 Web 表单和数据库数据。

## 4. 通用响应模型

### 4.1 分页响应

建议新增：

```java
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long total,
        boolean hasMore
) {
}
```

适用于现有列表功能：

```text
GET /api/notes
GET /api/projects
GET /api/tasks
```

约定：

- `page` 从 `0` 开始。
- `size` 默认 `20`。
- `size` 最小 `1`，最大 `100`。
- `total` 表示符合筛选条件的全部记录数。
- `hasMore` 由 `page`、`size` 和 `total` 计算。
- 默认排序为 `updatedAt DESC, id DESC`。

引入原因：

- 完善现有列表能力，而不是新增业务模块。
- Web 和 Agent 共用相同的翻页语义。
- 由 Backend 准确表达完整结果规模。

### 4.2 API 错误模型

保留现有：

```java
public record ApiError(
        String code,
        String message,
        LocalDateTime timestamp
) {
}
```

本阶段继续使用现有错误码：

```text
NOT_FOUND
BUSINESS_ERROR
BAD_REQUEST
DATA_INTEGRITY_ERROR
UNAUTHENTICATED
ACCESS_DENIED
```

分页或筛选参数错误统一归入 `BAD_REQUEST`，本阶段不新增更细的错误码。

原因：

- 当前重点是稳定数据模型，不提前扩展错误体系。
- 现有 Web 已能处理 `code` 和 `message`。

## 5. Note 数据模型

### 5.1 Note Entity

现有字段保持不变：

```text
id
title
content
folderId
folderName
projectId
projectName
version
createdAt
updatedAt
tags
```

说明：

- `content` 在数据库中是 Markdown `MEDIUMTEXT`。
- `folderName`、`projectName` 和 `tags` 是查询补充字段，不是 `notes` 表自身列。
- `version` 目前继续作为保存成功凭证，本阶段不扩展为乐观锁。

### 5.2 NoteSummaryResponse

建议新增：

```java
public record NoteSummaryResponse(
        Long id,
        String title,
        Long folderId,
        String folderName,
        Long projectId,
        String projectName,
        Long version,
        List<TagResponse> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
```

使用位置：

- 笔记列表。
- 笔记搜索结果。
- Dashboard 最近笔记。
- 项目详情中的关联笔记。

不包含 `content` 的原因：

- 现有 Web 在上述场景中不读取正文。
- 列表 SQL 可以完全避免读取 `MEDIUMTEXT`。
- MCP/Agent 可以先定位笔记，再调用详情接口读取正文。

### 5.3 NoteDetailResponse

建议新增：

```java
public record NoteDetailResponse(
        Long id,
        String title,
        String content,
        Long folderId,
        String folderName,
        Long projectId,
        String projectName,
        Long version,
        List<TagResponse> tags,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
```

使用位置：

- `GET /api/notes/{id}`。
- 创建笔记后的响应。
- 更新笔记后的响应。

保留完整正文的原因：

- Web 编辑器需要加载并确认实际保存的 Markdown。
- 创建和更新后返回完整持久化结果，保持现有行为。
- Agent 精确读取单篇笔记时也需要正文。

### 5.4 NoteRequest

保留现有字段：

```text
title
content
folderId
projectId
tagIds
```

明确语义：

- `POST /api/notes`：创建完整笔记。
- `PUT /api/notes/{id}`：完整替换可编辑字段，不是局部更新。
- `tagIds = null` 或空数组统一按无标签处理。

建议补充基础校验：

- `title` 必填，trim 后不能为空，最大 200 字符。
- `content` 必填。
- `folderId` 和 `projectId` 如果提供则必须存在。
- `tagIds` 去空、去重，并验证全部存在。

原因：

- 与数据库字段长度保持一致。
- 避免数据库异常代替清晰的业务错误。
- Web 和 Agent 获得一致的请求行为。

## 6. NoteFolder 数据模型

### 6.1 NoteFolderResponse

建议新增：

```java
public record NoteFolderResponse(
        Long id,
        String name,
        Long parentId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
```

本阶段保持扁平列表，不新增树形响应。

原因：

- 现有 Web 已在前端根据 `parentId` 构建文件夹树。
- 当前目标是模型分层，不新增导航接口。
- MCP/Agent 也可以根据相同结构理解层级关系。

### 6.2 NoteFolderRequest

保持：

```text
name
parentId
```

现有业务规则继续保留：

- 名称必填。
- 名称最大 120 字符。
- 父文件夹必须存在。
- 同级名称不能重复。
- 不能移动到自身或其子文件夹。
- 含子文件夹或笔记时不能删除。

## 7. Tag 数据模型

### 7.1 TagResponse

建议新增：

```java
public record TagResponse(
        Long id,
        String name,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
```

本阶段不增加 `noteCount` 等新字段。

原因：

- 当前功能只需要标签标识、名称和时间。
- 避免为了未来可能的标签统计增加查询和接口字段。
- `TagResponse` 可以被笔记摘要和详情复用。

### 7.2 TagRequest

保留 `name`，补充：

- trim 后不能为空。
- 最大 80 字符。
- 唯一性错误转换成明确的业务错误。

原因：

- 与 `tags.name VARCHAR(80)` 对齐。
- 不应把数据库唯一键异常直接暴露给调用方。

## 8. Project 数据模型

### 8.1 ProjectSummaryResponse

建议新增：

```java
public record ProjectSummaryResponse(
        Long id,
        String name,
        String description,
        ProjectStatus status,
        String repositoryUrl,
        String techStack,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
```

不包含 `runGuide`。

原因：

- 现有 Web 项目列表会显示 `description`。
- `status` 和 `techStack` 对 Web 与 Agent 都有意义。
- `runGuide` 可能较长，更适合详情接口。

### 8.2 ProjectResponse

项目完整响应建议为：

```java
public record ProjectResponse(
        Long id,
        String name,
        String description,
        ProjectStatus status,
        String repositoryUrl,
        String techStack,
        String runGuide,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
```

使用位置：

- 创建项目响应。
- 更新项目响应。
- 更新项目状态响应。
- 项目详情中的 `project` 字段。

### 8.3 ProjectDetailResponse

调整为：

```java
public record ProjectDetailResponse(
        ProjectResponse project,
        List<TaskResponse> tasks,
        List<NoteSummaryResponse> notes
) {
}
```

本阶段保持现有“项目详情返回全部关联任务和笔记”的功能，不新增关联分页；但关联笔记改用摘要，不返回正文。

原因：

- 先消除明显的大字段问题。
- 不在数据模型阶段改变现有项目详情交互。
- 后续确有数据规模问题时，再单独设计关联分页。

### 8.4 ProjectRequest

保持现有字段：

```text
name
description
status
repositoryUrl
techStack
runGuide
```

建议补充校验：

- `name` 必填，trim 后不能为空，最大 200 字符。
- `status` 必填。
- `repositoryUrl` 最大 500 字符。
- `techStack` 最大 500 字符。

原因：

- 与数据库字段长度一致。
- 创建和更新获得相同的确定行为。

## 9. Task 数据模型

### 9.1 TaskResponse

建议新增：

```java
public record TaskResponse(
        Long id,
        String title,
        String description,
        Long projectId,
        String projectName,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
```

本阶段不拆分 Task Summary 和 Detail，因为当前任务数据没有类似笔记正文的大字段，Web 列表与编辑功能也使用相同字段。

原因：

- 避免没有现实收益的重复 DTO。
- 当前一个稳定的 `TaskResponse` 足以覆盖已有列表、详情、看板和项目关联任务。

### 9.2 TaskRequest

保持现有字段：

```text
title
description
projectId
status
priority
dueDate
```

建议补充：

- `title` 必填，trim 后不能为空，最大 200 字符。
- `projectId` 必填且项目必须存在。
- `status` 必填。
- `priority` 必填。
- `dueDate` 继续允许为空。

本阶段不增加逾期、提醒或日期范围字段。

## 10. Dashboard 数据模型

调整现有 `DashboardResponse`：

```java
public record DashboardResponse(
        long activeProjectCount,
        long todoTaskCount,
        long inProgressTaskCount,
        List<ProjectSummaryResponse> recentProjects,
        List<NoteSummaryResponse> recentNotes
) {
}
```

本阶段不增加新的统计字段。

原因：

- 保持现有 Dashboard 功能和前端展示不变。
- 最近笔记不再携带完整正文。
- Dashboard 不再直接暴露 Entity。
- 不为未来功能预留尚未使用的字段。

## 11. Markdown 导入模型

保留：

```text
NoteImportRequest
NoteImportResponse
NoteImportItemResponse
NoteImportDuplicateStrategy
```

建议明确以下约定：

- `tagIds` 为 `null` 时按空集合处理。
- `tagIds` 过滤空值并去重。
- `folderId`、`projectId` 和全部 `tagIds` 必须存在。
- `duplicateStrategy` 必填。
- 单文件失败继续通过 `NoteImportItemResponse` 返回，不改变现有批量处理语义。
- `status` 保持 `IMPORTED`、`SKIPPED`、`FAILED`。

本阶段不改为异步任务，不新增导入任务模型。

原因：

- 保持现有导入功能不变。
- 仅把当前已经存在的行为明确为稳定契约。

## 12. Mapper 与查询调整

### 12.1 笔记查询拆分

建议明确区分：

```text
listSummaries(...)
findDetailById(id)
count(...)
```

`listSummaries` 不选择 `content`；`findDetailById` 才选择 `content`。

### 12.2 笔记标签批量加载

增加按一组笔记 ID 查询标签的 Mapper 方法，替代列表中的逐篇查询。

### 12.3 项目和任务分页

增加：

```text
listPage(...)
count(...)
```

本阶段不增加新的筛选字段，只保留现有筛选条件：

- 项目：`keyword`、`status`。
- 任务：`keyword`、`projectId`、`status`、`priority`。
- 笔记：`keyword`、`folderId`、`tagId`、`projectId`。

原因：

- 完善已有列表功能，不扩展搜索范围。
- 控制本次改动规模。

## 13. DTO 转换方式

建议由独立 Mapper/Assembler 完成 Entity 到 Response 的转换，例如：

```text
NoteResponseMapper
ProjectResponseMapper
TaskResponseMapper
TagResponseMapper
NoteFolderResponseMapper
```

转换器保持纯函数，不访问数据库。Service 先完成查询和关联数据加载，再调用转换器。

原因：

- 避免 Controller 手工拼装 DTO。
- 避免 DTO 自己依赖 Entity 或数据库。
- 转换逻辑可以独立单元测试。
- 同一种响应在列表、详情和 Dashboard 中保持一致。

如果团队认为单独的 Mapper 类过多，也可以先使用各 Response DTO 的静态 `from(...)` 方法；但整个项目应统一选择一种方式。

## 14. API 兼容影响

### 14.1 路由和 HTTP 方法保持不变

本阶段不新增或删除现有业务路由：

```text
/api/dashboard
/api/projects
/api/tasks
/api/notes
/api/note-folders
/api/tags
```

现有 CRUD 方法和状态码保持不变。

### 14.2 列表响应发生结构变化

项目、任务和笔记列表从数组变为 `PageResponse`：

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "total": 0,
  "hasMore": false
}
```

Web 需要同步从：

```javascript
items.value = await api.list(params);
```

调整为：

```javascript
const page = await api.list(params);
items.value = page.items;
```

### 14.3 笔记树的兼容处理

由于本阶段不新增导航接口，Web 笔记树可以暂时显式请求较大的页大小，但不能超过 Backend 上限。

更稳妥的实施选择有两个：

1. 本阶段暂不对笔记列表启用强制分页，只先引入 `NoteSummaryResponse` 并移除正文。
2. 同步实现专用导航接口，但这超出“只完善数据模型”的最小范围。

本方案推荐选择第一种：先完成笔记摘要/详情分离，项目和任务列表可先建立分页模型；笔记分页与导航改造留到后续独立任务。

## 15. 建议实施范围

### 第一批：纯数据模型边界

1. 新增 Response DTO。
2. Controller 不再直接返回 Entity。
3. 笔记摘要和详情分离。
4. Dashboard 和项目详情使用笔记摘要。
5. 为 Request 补齐与数据库一致的长度和必填校验。
6. 增加统一 Entity 到 Response 转换。
7. 补充 DTO 和 Controller 契约测试。

### 第二批：完善现有列表

1. 新增 `PageResponse<T>`。
2. 项目和任务列表实现分页。
3. 笔记列表是否分页与笔记导航方案一起确认。
4. 笔记标签改为批量加载。
5. 同步适配 Web 页面。

### 本阶段明确不做

- 不新增 Agent 专用接口。
- 不新增 Lookup 接口。
- 不新增任务统计和逾期能力。
- 不新增标签使用量。
- 不新增文件夹树响应。
- 不新增审计、幂等、回收站或软删除。
- 不修改现有角色权限。
- 不实现 MCP 业务 Tool。

## 16. 测试要求

### Backend

- 所有 Controller 返回 Response DTO，而不是 Entity。
- 笔记列表响应没有 `content`。
- 笔记详情、创建和更新响应包含 `content`。
- Dashboard 最近笔记没有 `content`。
- 项目详情关联笔记没有 `content`。
- Request 的必填和长度校验返回明确错误。
- DTO 转换字段完整且一致。
- 项目、任务、笔记现有筛选行为保持不变。
- 分页实施后验证页码、页大小、总数和稳定排序。
- 标签批量加载实施后验证标签关系完整。

### Frontend

- Dashboard 展示正常。
- 项目列表、详情、创建和编辑正常。
- 任务列表、看板、创建和编辑正常。
- 笔记树、搜索、创建、编辑和保存正常。
- 标签和文件夹管理正常。
- Markdown 导入正常。
- 执行 `npm.cmd run build` 并通过。

## 17. 验收标准

- API 不再直接返回数据库 Entity。
- 列表、详情和聚合响应的数据边界明确。
- 笔记列表、Dashboard 和项目详情不返回笔记正文。
- 笔记详情、创建和更新仍返回完整 Markdown。
- 当前 Web 功能和交互保持不变。
- 当前筛选、关联校验、删除约束和导入规则保持不变。
- Java 校验与数据库字段约束一致。
- Backend 测试通过。
- Frontend 生产构建通过。
- 没有引入未被当前功能使用的预留字段。

## 18. 最终建议

先实施“第一批：纯数据模型边界”，重点完成 Entity、Request 和 Response 的职责分离，以及笔记摘要/详情分离。

分页、笔记导航和更复杂的查询优化虽然有现实价值，但应作为下一批已有功能完善工作单独确认，避免在本次数据模型改造中同时改变过多 Web 交互。
