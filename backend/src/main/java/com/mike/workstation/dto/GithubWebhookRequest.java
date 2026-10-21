package com.mike.workstation.dto;
/** Secret 为空时沿用原值，配置版本防止并发覆盖。 */
public record GithubWebhookRequest(Boolean enabled,Integer expectedVersion,String secret) { }
