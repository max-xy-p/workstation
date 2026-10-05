package com.mike.workstation.dto;

import java.util.List;

/** 外部数据源接口契约。 */
public record IntegrationImportRequest(String requestKey, List<IntegrationImportItemRequest> items, Boolean sharingConfirmed) { }
