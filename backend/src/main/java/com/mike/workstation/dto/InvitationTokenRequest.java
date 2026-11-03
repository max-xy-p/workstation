package com.mike.workstation.dto;

/** 凭据通过请求体传递，避免出现在服务端访问日志中。 */
public record InvitationTokenRequest(String token) {}
