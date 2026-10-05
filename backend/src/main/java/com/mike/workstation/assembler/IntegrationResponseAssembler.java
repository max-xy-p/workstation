package com.mike.workstation.assembler;

import com.mike.workstation.domain.*;
import com.mike.workstation.dto.*;

/** 对外响应明确排除凭证、运行租约和内部配置。 */
public final class IntegrationResponseAssembler {
    private IntegrationResponseAssembler() { }
    public static IntegrationConnectionResponse connection(IntegrationConnection c) {
        return new IntegrationConnectionResponse(c.getId(), c.getName(), IntegrationProvider.valueOf(c.getProvider()),
                c.getBaseUrl(), c.getAccount(), IntegrationScope.valueOf(c.getScope()), c.isIncludeForks(),
                c.isIncludeArchived(), c.isAutoImport(), c.getScheduleMinutes(), c.isEnabled(), c.getVersion(),
                c.getLastError(), ApiTime.toOffset(c.getLastSyncedAt()));
    }
    public static RepositoryResponse repository(ExternalRepository r) {
        return new RepositoryResponse(r.getId(), IntegrationProvider.valueOf(r.getProvider()), r.getNamespace(),
                r.getName(), r.getUrl(), r.getDescription(), r.getDefaultBranch(), r.getLanguage(), r.getVisibility(),
                r.isArchived(), r.getProjectId(), r.getConnectionId(), ApiTime.toOffset(r.getLastPushedAt()),
                ApiTime.toOffset(r.getLastSyncedAt()), r.getLastError(), r.getCollectedOpenIssues(), r.getCollectedOpenChangeRequests(), r.getLatestPipelineStatus());
    }
    public static IntegrationRunResponse run(IntegrationRun r) {
        return new IntegrationRunResponse(r.getId(), r.getKind(), r.getStatus(), r.getProcessed(), r.getFailed(),
                r.getLastError(), ApiTime.toOffset(r.getCreatedAt()), ApiTime.toOffset(r.getFinishedAt()));
    }
    public static IntegrationRunItemResponse item(IntegrationRunItem i) {
        return new IntegrationRunItemResponse(i.getId(), i.getRepositoryId(), i.getProjectId(), i.getStatus(), i.getLastError(), ProjectStatus.valueOf(i.getProjectStatus()));
    }
    public static DevelopmentSnapshotResponse snapshot(DevelopmentSnapshot s) {
        return new DevelopmentSnapshotResponse(s.getRepositoryId(), DevelopmentKind.valueOf(s.getKind()), s.isAvailable(),
                s.isTruncated(), s.getMessage(), ApiTime.toOffset(s.getSince()), ApiTime.toOffset(s.getSyncedAt()),
                s.getCollected(), s.getOpenCount());
    }
    public static DevelopmentItemResponse development(DevelopmentItem i) {
        return new DevelopmentItemResponse(i.getId(), i.getRepositoryId(), DevelopmentKind.valueOf(i.getKind()),
                i.getExternalId(), i.getTitle(), i.getUrl(), i.getStatus(), i.getRawStatus(), i.getAuthor(), i.getBranch(),
                ApiTime.toOffset(i.getSourceUpdatedAt()));
    }
}
