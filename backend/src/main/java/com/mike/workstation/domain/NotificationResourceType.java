package com.mike.workstation.domain;

/** 通知只能指向已知站内资源，不保存任意跳转 URL。 */
public enum NotificationResourceType {
    WORKSPACE, PROJECT, TASK, NOTE
}
