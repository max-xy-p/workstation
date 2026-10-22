package com.mike.workstation.domain;

/** Markdown 导入时遇到同标题笔记的处理策略。 */
public enum NoteImportDuplicateStrategy {
    CREATE_NEW,
    SKIP_TITLE
}
