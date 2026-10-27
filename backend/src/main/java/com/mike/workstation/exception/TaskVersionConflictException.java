package com.mike.workstation.exception;

/** 用户所见任务版本已过期，禁止用旧内容覆盖当前任务。 */
public class TaskVersionConflictException extends RuntimeException {
    public TaskVersionConflictException(String message) {
        super(message);
    }
}
