USE workstation;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE note_tag;
TRUNCATE TABLE notes;
TRUNCATE TABLE tasks;
TRUNCATE TABLE tags;
TRUNCATE TABLE note_folders;
TRUNCATE TABLE projects;
SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO note_folders (name, parent_id, created_at, updated_at) VALUES
('开发', NULL, '2026-07-01 09:10:00', '2026-07-17 18:20:00'),
('学习', NULL, '2026-07-05 08:45:00', '2026-07-18 09:30:00');

SET @folder_dev = (SELECT id FROM note_folders WHERE name = '开发' AND parent_id IS NULL);
SET @folder_study = (SELECT id FROM note_folders WHERE name = '学习' AND parent_id IS NULL);

INSERT INTO note_folders (name, parent_id, created_at, updated_at) VALUES
('后端开发', @folder_dev, '2026-07-01 09:10:00', '2026-07-17 18:20:00'),
('前端工程', @folder_dev, '2026-07-02 10:00:00', '2026-07-16 16:35:00'),
('数据库', @folder_dev, '2026-07-03 11:30:00', '2026-07-15 20:15:00'),
('运维部署', @folder_dev, '2026-07-04 14:00:00', '2026-07-18 12:40:00'),
('学习笔记', @folder_study, '2026-07-05 08:45:00', '2026-07-18 09:30:00');

INSERT INTO tags (name, created_at, updated_at) VALUES
('Spring Boot', '2026-07-01 09:20:00', '2026-07-17 18:20:00'),
('MyBatis', '2026-07-01 09:25:00', '2026-07-17 18:20:00'),
('Vue', '2026-07-02 10:10:00', '2026-07-16 16:35:00'),
('MySQL', '2026-07-03 11:40:00', '2026-07-15 20:15:00'),
('Nginx', '2026-07-04 14:10:00', '2026-07-18 12:40:00'),
('Redis', '2026-07-06 09:00:00', '2026-07-12 17:20:00'),
('需求分析', '2026-07-06 09:30:00', '2026-07-13 11:00:00'),
('Bug排查', '2026-07-07 15:40:00', '2026-07-18 10:25:00'),
('性能优化', '2026-07-08 16:00:00', '2026-07-14 19:10:00'),
('本地工具', '2026-07-09 19:30:00', '2026-07-18 13:00:00');

INSERT INTO projects (name, description, status, repository_url, tech_stack, run_guide, created_at, updated_at) VALUES
('个人工作台', '本机使用的项目、任务和技术笔记管理工具，目标是把日常开发上下文集中到一个入口。', 'ACTIVE', 'https://github.com/mike/workstation', 'Java 17, Spring Boot 4, MyBatis, MySQL, Vue 3, Vite, Nginx', '后端运行 mvn spring-boot:run，前端运行 npm.cmd run dev，通过 nginx 的 8088 端口统一访问。', '2026-07-10 09:00:00', '2026-07-18 15:30:00'),
('Notion 笔记迁移工具', '将 Notion 导出的 Markdown 笔记批量导入个人知识库，保留标题、正文、文件夹和标签关系。', 'ACTIVE', NULL, 'Java, Markdown, MySQL', '配置 NOTE_IMPORT_ALLOWED_ROOTS 后，从笔记页填写导出目录并执行导入。', '2026-07-12 10:15:00', '2026-07-18 15:45:00'),
('个人博客重构', '把旧博客迁移为静态站点，补充技术文章分类、搜索和部署流水线。', 'PLANNING', 'https://github.com/mike/blog-next', 'Vue, Markdown, Nginx', '先整理文章目录，再设计路由和构建脚本。', '2026-06-25 20:00:00', '2026-07-14 21:10:00'),
('代码片段管理器', '收集常用 SQL、Shell、Java 片段，支持按标签快速检索。', 'PAUSED', NULL, 'Spring Boot, MySQL, Vue', '暂停中，后续考虑和个人工作台合并。', '2026-05-18 13:30:00', '2026-07-08 18:00:00'),
('本地开发环境整理', '统一整理 JDK、Maven、Node、MySQL、Nginx 等本机开发环境和启动脚本。', 'COMPLETED', NULL, 'Windows, Maven, Node.js, MySQL, Nginx', '按 README 检查环境变量和端口占用。', '2026-07-01 19:20:00', '2026-07-11 22:30:00'),
('家庭账本 API', '用于记录家庭收支流水和月度预算的小型 REST 服务。', 'ARCHIVED', 'https://github.com/mike/home-ledger-api', 'Java 17, Spring Boot, MySQL', '归档项目，仅保留代码和数据库设计参考。', '2026-04-06 08:40:00', '2026-06-30 17:00:00');

SET @workstation = (SELECT id FROM projects WHERE name = '个人工作台');
SET @notion = (SELECT id FROM projects WHERE name = 'Notion 笔记迁移工具');
SET @blog = (SELECT id FROM projects WHERE name = '个人博客重构');
SET @snippet = (SELECT id FROM projects WHERE name = '代码片段管理器');
SET @env = (SELECT id FROM projects WHERE name = '本地开发环境整理');
SET @ledger = (SELECT id FROM projects WHERE name = '家庭账本 API');

INSERT INTO tasks (title, description, project_id, status, priority, due_date, created_at, updated_at) VALUES
('完成项目与任务 CRUD API', '补齐项目列表、详情、创建、编辑、删除和状态更新接口。', @workstation, 'DONE', 'HIGH', '2026-07-16', '2026-07-10 10:00:00', '2026-07-16 18:20:00'),
('接入 Vue 任务看板', '看板按 TODO、IN_PROGRESS、DONE 分列展示，并支持状态流转。', @workstation, 'DONE', 'HIGH', '2026-07-17', '2026-07-11 09:30:00', '2026-07-17 20:10:00'),
('完善 nginx 本地代理配置', '通过 nginx 统一前端和 API 入口，避免浏览器跨域问题。', @workstation, 'IN_PROGRESS', 'MEDIUM', '2026-07-19', '2026-07-18 10:00:00', '2026-07-18 14:30:00'),
('给首页补真实样例数据', '准备一批贴近日常开发场景的项目、任务和笔记，便于验收页面状态。', @workstation, 'IN_PROGRESS', 'LOW', '2026-07-18', '2026-07-18 16:00:00', '2026-07-18 16:10:00'),
('补充导入结果明细展示', '导入后展示成功、跳过、失败列表，方便排查单个文件问题。', @notion, 'IN_PROGRESS', 'HIGH', '2026-07-20', '2026-07-18 15:00:00', '2026-07-18 15:50:00'),
('处理 Markdown 图片资源', '评估 Notion 导出图片是否需要复制到应用资源目录，并改写正文链接。', @notion, 'TODO', 'MEDIUM', '2026-07-24', '2026-07-18 15:10:00', '2026-07-18 15:10:00'),
('增加导入路径安全校验', '只允许读取 NOTE_IMPORT_ALLOWED_ROOTS 下的 Markdown，避免页面传任意系统路径。', @notion, 'DONE', 'HIGH', '2026-07-18', '2026-07-18 15:05:00', '2026-07-18 15:40:00'),
('确认笔记导入后的去重策略', '比较跳过同标题和总是新建两种策略，决定默认行为。', @notion, 'TODO', 'MEDIUM', NULL, '2026-07-18 16:05:00', '2026-07-18 16:05:00'),
('梳理博客文章分类', '把历史文章按 Java、数据库、部署、问题复盘重新分类。', @blog, 'TODO', 'MEDIUM', '2026-07-28', '2026-07-14 20:00:00', '2026-07-14 20:00:00'),
('设计博客列表页信息密度', '避免营销页风格，优先做可扫描的文章列表和标签过滤。', @blog, 'TODO', 'LOW', NULL, '2026-07-14 21:00:00', '2026-07-14 21:00:00'),
('整理 Maven 本地仓库配置', '确认 Maven 不向安装目录写依赖，统一使用项目 target/maven-repo 做验证。', @env, 'DONE', 'MEDIUM', '2026-07-11', '2026-07-05 19:30:00', '2026-07-11 22:00:00'),
('记录 npm PowerShell 执行策略处理方式', 'Windows 下使用 npm.cmd 避免 npm.ps1 执行策略限制。', @env, 'DONE', 'LOW', '2026-07-10', '2026-07-06 13:20:00', '2026-07-10 15:40:00'),
('抽象代码片段标签模型', '保留标签多对多关系，后续考虑复用笔记标签表。', @snippet, 'TODO', 'LOW', NULL, '2026-07-08 17:30:00', '2026-07-08 18:00:00'),
('归档账本 API 部署说明', '把端口、数据库和历史迁移脚本整理到项目运行说明。', @ledger, 'DONE', 'LOW', '2026-06-30', '2026-06-20 11:00:00', '2026-06-30 17:00:00');

SET @folder_backend = (SELECT id FROM note_folders WHERE name = '后端开发');
SET @folder_frontend = (SELECT id FROM note_folders WHERE name = '前端工程');
SET @folder_db = (SELECT id FROM note_folders WHERE name = '数据库');
SET @folder_ops = (SELECT id FROM note_folders WHERE name = '运维部署');
SET @folder_notes = (SELECT id FROM note_folders WHERE name = '学习笔记');

INSERT INTO notes (title, content, folder_id, project_id, created_at, updated_at) VALUES
('Spring Boot 配置拆分记录', CONCAT('# Spring Boot 配置拆分记录', CHAR(10), CHAR(10), '本机工作台优先把数据库、导入路径、首页展示数量放到 .env。业务枚举留在代码里，展示文案放前端常量。', CHAR(10), CHAR(10), '## 决策', CHAR(10), '- 不在后端维护 CORS', CHAR(10), '- SQL 建表脚本手动执行', CHAR(10), '- 导入目录通过 NOTE_IMPORT_ALLOWED_ROOTS 白名单控制'), @folder_backend, @workstation, '2026-07-17 18:00:00', '2026-07-18 14:50:00'),
('Nginx 统一入口排查清单', CONCAT('# Nginx 统一入口排查清单', CHAR(10), CHAR(10), '当前推荐通过 http://localhost:8088 访问工作台。', CHAR(10), CHAR(10), '- / 代理到 Vite 5173', CHAR(10), CHAR(10), '- /api/ 代理到 Spring Boot 8080', CHAR(10), '- 如果看到 <!doctype html>，通常是 API 请求打到了前端服务'), @folder_ops, @workstation, '2026-07-18 12:30:00', '2026-07-18 14:35:00'),
('Notion Markdown 导入策略', CONCAT('# Notion Markdown 导入策略', CHAR(10), CHAR(10), '导入时优先使用 Markdown 一级标题作为笔记标题；如果没有标题，就从文件名中去掉 Notion 32 位 ID 后缀。', CHAR(10), CHAR(10), '## 暂不处理', CHAR(10), '- 图片复制', CHAR(10), '- 附件搬运', CHAR(10), '- 双链关系恢复'), @folder_backend, @notion, '2026-07-18 15:20:00', '2026-07-18 15:55:00'),
('Vue 工作台页面结构', CONCAT('# Vue 工作台页面结构', CHAR(10), CHAR(10), '页面以工具为主，不做营销首页。导航固定在左侧，主要页面包括概览、项目、任务看板、笔记、文件夹和标签。', CHAR(10), CHAR(10), '## 交互原则', CHAR(10), '- 列表页直接提供筛选', CHAR(10), '- 表单和结果尽量同屏', CHAR(10), '- 空状态要清楚'), @folder_frontend, @workstation, '2026-07-16 16:00:00', '2026-07-18 13:30:00'),
('MySQL 表关系说明', CONCAT('# MySQL 表关系说明', CHAR(10), CHAR(10), '项目和任务是一对多，笔记可选关联项目和文件夹，笔记与标签是多对多。', CHAR(10), CHAR(10), '删除规则要在 service 层显式处理，避免数据库级联造成用户误解。'), @folder_db, @workstation, '2026-07-15 19:45:00', '2026-07-18 10:10:00'),
('Maven 本地仓库踩坑', CONCAT('# Maven 本地仓库踩坑', CHAR(10), CHAR(10), '系统 Maven 默认仓库指向安装目录时，沙箱或普通用户权限可能无法写入。', CHAR(10), CHAR(10), '验证命令固定为：', CHAR(10), CHAR(10), 'mvn -Dmaven.repo.local=target/maven-repo test'), @folder_notes, @env, '2026-07-11 21:20:00', '2026-07-11 22:20:00'),
('任务看板状态定义', CONCAT('# 任务看板状态定义', CHAR(10), CHAR(10), '- TODO：还没开始但已经明确要做', CHAR(10), '- IN_PROGRESS：正在推进，有下一步动作', CHAR(10), '- DONE：已经完成并通过基本验证', CHAR(10), CHAR(10), '第一版不引入 blocked/review，避免流程变重。'), @folder_notes, @workstation, '2026-07-13 10:30:00', '2026-07-17 20:30:00'),
('家庭账本 API 归档说明', CONCAT('# 家庭账本 API 归档说明', CHAR(10), CHAR(10), '项目已归档，后续只作为 REST API 分层和数据库索引设计参考。', CHAR(10), CHAR(10), '值得保留的内容：预算聚合查询、流水分页、按月统计接口。'), @folder_backend, @ledger, '2026-06-30 16:30:00', '2026-06-30 17:00:00');

SET @tag_spring = (SELECT id FROM tags WHERE name = 'Spring Boot');
SET @tag_mybatis = (SELECT id FROM tags WHERE name = 'MyBatis');
SET @tag_vue = (SELECT id FROM tags WHERE name = 'Vue');
SET @tag_mysql = (SELECT id FROM tags WHERE name = 'MySQL');
SET @tag_nginx = (SELECT id FROM tags WHERE name = 'Nginx');
SET @tag_req = (SELECT id FROM tags WHERE name = '需求分析');
SET @tag_bug = (SELECT id FROM tags WHERE name = 'Bug排查');
SET @tag_local = (SELECT id FROM tags WHERE name = '本地工具');

SET @note_config = (SELECT id FROM notes WHERE title = 'Spring Boot 配置拆分记录');
SET @note_nginx = (SELECT id FROM notes WHERE title = 'Nginx 统一入口排查清单');
SET @note_import = (SELECT id FROM notes WHERE title = 'Notion Markdown 导入策略');
SET @note_vue = (SELECT id FROM notes WHERE title = 'Vue 工作台页面结构');
SET @note_schema = (SELECT id FROM notes WHERE title = 'MySQL 表关系说明');
SET @note_maven = (SELECT id FROM notes WHERE title = 'Maven 本地仓库踩坑');
SET @note_board = (SELECT id FROM notes WHERE title = '任务看板状态定义');
SET @note_ledger = (SELECT id FROM notes WHERE title = '家庭账本 API 归档说明');

INSERT INTO note_tag (note_id, tag_id) VALUES
(@note_config, @tag_spring), (@note_config, @tag_local), (@note_config, @tag_req),
(@note_nginx, @tag_nginx), (@note_nginx, @tag_bug), (@note_nginx, @tag_local),
(@note_import, @tag_spring), (@note_import, @tag_mybatis), (@note_import, @tag_local),
(@note_vue, @tag_vue), (@note_vue, @tag_req),
(@note_schema, @tag_mysql), (@note_schema, @tag_mybatis),
(@note_maven, @tag_local), (@note_maven, @tag_bug),
(@note_board, @tag_req),
(@note_ledger, @tag_spring), (@note_ledger, @tag_mysql);
