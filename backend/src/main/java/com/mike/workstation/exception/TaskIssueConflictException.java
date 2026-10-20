package com.mike.workstation.exception;

/** 来源已关联其他任务或任务已有来源。 */
public class TaskIssueConflictException extends RuntimeException {
    public TaskIssueConflictException(String message) { super(message); }
}
