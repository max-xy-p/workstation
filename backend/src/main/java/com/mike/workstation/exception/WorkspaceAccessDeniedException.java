package com.mike.workstation.exception;

/** 主体属于工作空间但缺少目标 Permission。 */
public class WorkspaceAccessDeniedException extends RuntimeException {
    public WorkspaceAccessDeniedException(String message) {
        super(message);
    }
}
