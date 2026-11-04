package com.mike.workstation.domain;

/** MQ 只广播用户标识和已提交版本，重复或乱序提醒均通过 HTTP 读取最新状态。 */
public record NotificationRefresh(String recipient, long revision) {}
