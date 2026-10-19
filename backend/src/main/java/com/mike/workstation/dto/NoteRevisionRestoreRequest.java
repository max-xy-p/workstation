package com.mike.workstation.dto;

/** 恢复历史时必须提交当前已知的笔记版本。 */
public record NoteRevisionRestoreRequest(Long expectedVersion) { }
