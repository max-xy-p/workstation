package com.mike.workstation.dto;
/** 只返回路由和状态，不返回任何密钥。 */
public record GithubWebhookResponse(boolean configured,boolean enabled,int version,String endpointPath) { }
