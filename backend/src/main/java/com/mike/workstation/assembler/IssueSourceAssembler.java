package com.mike.workstation.assembler;

import com.mike.workstation.domain.IssueSource;
import com.mike.workstation.dto.TaskIssueSourceResponse;

/** 对外只暴露来源摘要，不返回连接凭证或执行租约。 */
public final class IssueSourceAssembler {
    private IssueSourceAssembler() { }
    public static TaskIssueSourceResponse response(IssueSource s) {
        return new TaskIssueSourceResponse(s.getId(),s.getTaskId(),s.getProjectId(),s.getRepositoryId(),
                s.getRepositoryName(),s.getExternalId(),s.getIssueNumber(),s.getTitle(),s.getUrl(),s.getStatus(),
                s.getStateReason(),ApiTime.toOffset(s.getSourceUpdatedAt()),ApiTime.toOffset(s.getLastAttemptAt()),
                ApiTime.toOffset(s.getLastSuccessAt()),s.getLastError(),s.getTrackingStatus());
    }
}
