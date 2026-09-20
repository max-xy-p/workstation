package com.mike.workstation.service;

/** 单次 Outbox Relay 的领取、成功和失败数量。 */
public record AuditRelayResult(int claimed, int published, int failed) {
}
