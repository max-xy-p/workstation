# Backend 规范

- 沿用 `Controller -> Service -> Mapper` 分层。
- Controller 只处理 HTTP 参数、状态码和 DTO，不承载事务或持久化规则。
- Service 负责业务校验、关联对象存在性和事务；写操作使用 `@Transactional`。
- Mapper 使用 MyBatis 注解 SQL，并避免列表场景的逐条关联查询。
- Java 领域对象使用 Lombok `@Data`；请求和组合响应优先使用 Java `record`。
- 数据库 Entity、Request DTO 和 Response DTO 分离，Entity 不直接作为 API Response。
- API 位于 `/api` 并使用 JSON。
- 创建成功返回 `201 Created`。
- 删除成功且无响应体时返回 `204 No Content`。
- 业务异常统一由 `GlobalExceptionHandler` 转换为契约规定的错误结构。
- 历史数据库变更提供独立迁移脚本；不要假设已有数据卷会重新执行初始化脚本。
- Backend 测试沿用 JUnit 5、Mockito 和 MockMvc。
- Service 规则变化补 Service 测试。
- HTTP 契约变化补 Controller 或集成测试。
- 避免 N+1 查询；列表和聚合场景优先使用批量查询或适当 JOIN。
- 事务边界放在业务层，不在 Controller、Mapper 或辅助转换对象中启动业务事务。
- DTO 与 Entity 之间的转换沿用现有 Assembler / Mapper 方式，不在 Controller 中堆积字段转换逻辑。
- 对权限、状态转换、幂等性、版本控制等核心业务约束，应由服务端统一保证。

## Java 可读性约定

- 类型引用使用显式 `import` 和短类名；只有存在同名类型、内部类或类型参数冲突时保留必要的全限定名。反射、驱动等需要完整名称的字符串不做缩写。
- 使用四空格缩进，拆开同一行中的多条语句；条件与循环体使用花括号，导入、换行和空白保持一致。
- 注释使用中文，重点解释职责边界、事务与锁顺序、版本和幂等保护、失败恢复的原因；不逐行复述显然的赋值或访问器。类与方法的文档注释放在注解之前。
