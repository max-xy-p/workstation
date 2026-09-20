package com.mike.workstation.domain;

import java.util.EnumSet;
import java.util.Set;

/** Backend 业务授权使用的细粒度权限。 */
public enum WorkspacePermission {
    WORKSPACE_READ,
    WORKSPACE_WRITE,
    MEMBER_READ,
    MEMBER_MANAGE,
    DASHBOARD_READ,
    NOTE_READ,
    NOTE_WRITE,
    NOTE_IMPORT,
    FOLDER_READ,
    FOLDER_WRITE,
    TAG_READ,
    TAG_WRITE,
    PROJECT_READ,
    PROJECT_WRITE,
    TASK_READ,
    TASK_WRITE,
    AUDIT_READ;

    private static final Set<WorkspacePermission> OWNER_PERMISSIONS = EnumSet.allOf(WorkspacePermission.class);
    private static final Set<WorkspacePermission> ADMIN_PERMISSIONS = EnumSet.complementOf(
            EnumSet.noneOf(WorkspacePermission.class));
    private static final Set<WorkspacePermission> EDITOR_PERMISSIONS = EnumSet.of(
            WORKSPACE_READ, DASHBOARD_READ,
            NOTE_READ, NOTE_WRITE, NOTE_IMPORT,
            FOLDER_READ, FOLDER_WRITE,
            TAG_READ, TAG_WRITE,
            PROJECT_READ, PROJECT_WRITE,
            TASK_READ, TASK_WRITE
    );
    private static final Set<WorkspacePermission> VIEWER_PERMISSIONS = EnumSet.of(
            WORKSPACE_READ, DASHBOARD_READ, NOTE_READ, FOLDER_READ, TAG_READ, PROJECT_READ, TASK_READ
    );

    public static Set<WorkspacePermission> forRole(WorkspaceRole role) {
        return switch (role) {
            case OWNER -> EnumSet.copyOf(OWNER_PERMISSIONS);
            case ADMIN -> EnumSet.copyOf(ADMIN_PERMISSIONS);
            case EDITOR -> EnumSet.copyOf(EDITOR_PERMISSIONS);
            case VIEWER -> EnumSet.copyOf(VIEWER_PERMISSIONS);
        };
    }
}
