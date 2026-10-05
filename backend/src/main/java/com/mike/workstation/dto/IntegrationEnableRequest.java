package com.mike.workstation.dto;


/** 外部数据源接口契约。 */
public record IntegrationEnableRequest(Boolean enabled, Integer expectedVersion, Boolean sharingConfirmed) { }
