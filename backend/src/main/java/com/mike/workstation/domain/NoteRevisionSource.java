package com.mike.workstation.domain;

/** 笔记内容快照的服务端来源。 */
public enum NoteRevisionSource {
    CREATE,
    IMPORT,
    UPDATE,
    RESTORE,
    BASELINE
}
