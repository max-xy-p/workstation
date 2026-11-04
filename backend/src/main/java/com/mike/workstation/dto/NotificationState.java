package com.mike.workstation.dto;

/** 已提交序号用于分页与全部已读边界，revision 同时反映已读变更。 */
public record NotificationState(boolean enabled, long unreadCount, long latestSequence, long revision) {}
