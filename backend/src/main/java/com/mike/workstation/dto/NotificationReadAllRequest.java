package com.mike.workstation.dto;

/** 不允许把客户端尚未看到序号之后的新通知一起标记已读。 */
public record NotificationReadAllRequest(Long throughSequence) {}
