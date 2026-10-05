package com.mike.workstation.dto;

import com.mike.workstation.domain.IntegrationProvider;
import com.mike.workstation.domain.IntegrationScope;
import java.time.OffsetDateTime;

/** 外部数据源接口契约。 */
public record IntegrationConnectionResponse(Long id, String name, IntegrationProvider provider, String baseUrl, String account, IntegrationScope scope, Boolean includeForks, Boolean includeArchived, Boolean autoImport, Integer scheduleMinutes, Boolean enabled, Integer version, String lastError, OffsetDateTime lastSyncedAt) { }
