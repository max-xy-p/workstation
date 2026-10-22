package com.mike.workstation.dto;

import com.mike.workstation.domain.IntegrationProvider;
import com.mike.workstation.domain.IntegrationScope;

/** 外部数据源接口契约。 */
public record IntegrationConnectionRequest(
        String name,
        IntegrationProvider provider,
        String baseUrl,
        String token,
        IntegrationScope scope,
        Boolean includeForks,
        Boolean includeArchived,
        Boolean autoImport,
        Integer scheduleMinutes,
        Integer expectedVersion,
        Boolean sharingConfirmed) {}
