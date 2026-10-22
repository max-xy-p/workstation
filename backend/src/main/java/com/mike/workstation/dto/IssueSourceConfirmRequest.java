package com.mike.workstation.dto;

/** 确认严格针对用户实际查看的来源、关联与任务版本。 */
public record IssueSourceConfirmRequest(
        String expectedLinkToken,
        Long expectedSourceVersion,
        Long expectedConfirmationVersion,
        Long expectedTaskVersion,
        Action action) {
    public enum Action {
        KEEP,
        COMPLETE,
        REOPEN
    }
}
