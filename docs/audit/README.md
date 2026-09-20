# 审计事件与异步投递说明

## 目标与边界

审计用于回答“谁在什么 Workspace 对什么资源执行了什么操作，结果如何”。它是安全追踪与问题排查能力，不是业务数据真源，也不能记录 Token、Secret、密码或笔记正文。

业务可用性和审计可靠性的边界如下：

- 成功写操作与一条待投递审计事件在同一个 MySQL 事务中提交，避免业务回滚后留下虚假成功记录。
- RabbitMQ、XXL-JOB 或最终审计表故障不会回滚已经提交的业务；事件留在 Outbox 等待恢复。
- 权限拒绝没有可提交的业务事务，使用独立事务尽力写入 Outbox；审计失败不能把原有 `403` 或隐藏资源的 `404` 改写为 `500`。
- 如果连本地 Outbox 都不能随业务事务写入，成功写操作会失败。这是“不丢事件”与“不使用分布式事务”共同决定的取舍。

## 数据流

```text
业务 Service
  -> 业务表 + audit_outbox（同一事务）
  -> 返回业务结果

XXL-JOB
  -> auditOutboxRelayJob
  -> 短事务领取 PENDING 或租约过期的 SENDING 事件
  -> RabbitMQ 持久化消息 + Publisher Confirm
  -> audit_outbox 标记 PUBLISHED 或安排退避重试

RabbitMQ
  -> AuditMessageConsumer
  -> 独立事务幂等写入 audit_events
  -> 成功后 ACK；失败有限重试；耗尽后进入死信队列
```

XXL-JOB 只负责调度、执行日志、手工触发和系统级失败重试。单条 Outbox 的尝试次数、退避时间和最终失败状态由 Backend 管理，避免一条消息失败导致整批成功消息再次发布。

## 一致性与幂等

`audit_outbox.event_id` 是事件在生产、传输和消费阶段的稳定标识，`audit_events.event_id` 具有唯一约束。消费者可能在“落库成功、发送 ACK 前”中断，因此 RabbitMQ 采用 At-least-once 语义，重复事件由最终表的唯一约束幂等吸收。

Relay 领取记录时使用短事务、`SKIP LOCKED` 和租约，不在等待 RabbitMQ 网络确认时持有数据库行锁。执行器中断后，租约到期的 `SENDING` 记录可以再次领取。

Outbox 状态含义：

| 状态 | 含义 |
| --- | --- |
| `PENDING` | 等待首次投递或退避后重试 |
| `SENDING` | 已被某个 Relay 领取，租约内不重复领取 |
| `PUBLISHED` | Broker 已确认接收，可按保留策略清理 |
| `FAILED` | 达到最大发布次数，需要人工检查和重新投递 |

## RabbitMQ 拓扑

主 Exchange 按固定 Routing Key 将事件路由到持久化审计队列。消费者连续失败并耗尽重试后，消息进入独立死信 Exchange 和死信队列，不进行无限立即重入队。

RabbitMQ 管理页面只绑定宿主机回环地址，用于查看队列深度、消息速率、消费者和死信；它不是应用公网入口。

## XXL-JOB 运维

调度中心使用独立 `xxl_job` 数据库和账号，Backend 作为 `workstation-backend` 执行器注册。全新数据卷会预置固定间隔触发的 `auditOutboxRelayJob`；任务采用单机串行和普通路由，不启用分片广播。

调度中心不可用时，业务和 Outbox 写入继续运行，未投递事件在 MySQL 中积压。恢复后任务继续搬运。任务日志只记录批次数量和事件标识，不记录审计载荷中的敏感字段。

XXL-JOB 管理页面和执行器端口均只应位于可信网络；调度中心与执行器必须配置非默认 Access Token。管理页面密码、数据库密码和 SSO Token Key 通过部署环境提供。

## 故障处理

| 故障 | 预期行为 |
| --- | --- |
| RabbitMQ 不可用或 Confirm 超时 | 事件退回 `PENDING` 并指数退避，业务不回滚 |
| XXL-JOB 暂停或调度中心不可用 | Outbox 积压，业务继续运行 |
| Relay 实例中断 | `SENDING` 租约到期后重新领取 |
| 消费者写最终表失败 | RabbitMQ 有限重试，耗尽后进入死信队列 |
| 消息重复投递 | `event_id` 唯一约束保证幂等 |
| 权限拒绝事件写 Outbox 失败 | 保持原授权响应，同时写内部错误日志 |
| 成功业务事务写 Outbox 失败 | 整个本地事务回滚，不返回虚假成功 |

排查顺序是：先看 XXL-JOB 本次任务是否被触发，再看 `audit_outbox` 的状态和错误摘要，然后查看 RabbitMQ 主队列与死信队列，最后确认消费者和 `audit_events`。修复后可以在 XXL-JOB 手工触发 Relay；重复投递不会重复生成最终审计事件。

## 数据库升级

全新环境由初始化脚本创建结构。已有 Workstation 数据卷不会重新执行初始化脚本，必须先备份，并在 Workspace 迁移完成后单独执行 `backend/src/main/resources/db/migrate-audit-messaging.sql`。

已有 MySQL 数据卷还需要初始化 XXL-JOB 官方表和独立数据库账号；仓库中的版本化表结构位于 `xxl-job/tables_xxl_job.sql`。这些操作不会由容器重建自动替代，也没有在本次开发中执行。
