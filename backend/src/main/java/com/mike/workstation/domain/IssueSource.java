package com.mike.workstation.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Objects;

/** 已关联 Issue 的持久化来源，不随近期开发快照重建。 */
@Data
public class IssueSource {
    // 来源表的业务版本；读取失败或内容相同的重复刷新不会推进该版本。
    private Long sourceVersion;
    // 以下任务版本与确认字段由关联查询补齐，不是来源表自身的持久化字段。
    private Long taskVersion;
    private Long confirmationVersion;
    private String confirmedTitle;
    private String confirmedStatus;
    private String confirmedStateReason;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    // 外部来源的稳定身份及最近成功快照，不依赖近期采集记录的数据库 ID。
    private Long id;
    private Long workspaceId;
    private Long repositoryId;
    private String provider;
    private String baseUrl;
    private String externalId;
    private Long issueNumber;
    private String title;
    private String url;
    private String status;
    private String stateReason;
    private LocalDateTime sourceUpdatedAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime lastSuccessAt;
    private String lastError;
    // 当前关联及展示上下文；跟踪状态由绑定、连接权限和读取结果共同推导。
    private Long taskId;
    private String linkToken;
    private Long projectId;
    private String repositoryName;
    private String trackingStatus;

    /** 待处理只取决于业务差异，读取失败或停止跟踪不清除确认基准。 */
    public boolean hasPendingChanges() {
        return !Objects.equals(title, confirmedTitle)
                || !Objects.equals(status, confirmedStatus)
                || !Objects.equals(stateReason, confirmedStateReason);
    }
}
