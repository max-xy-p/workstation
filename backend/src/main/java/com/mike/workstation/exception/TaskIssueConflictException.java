package com.mike.workstation.exception;

/** 来源关联冲突，或确认所依据的来源、关联、任务版本已变化。 */
public class TaskIssueConflictException extends RuntimeException {
    public TaskIssueConflictException(String message) {
        super(message);
    }
}
