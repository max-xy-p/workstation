package com.mike.workstation.exception;

/** 笔记已由其他请求更新，当前版本不能直接覆盖。 */
public class NoteVersionConflictException extends RuntimeException {
    public NoteVersionConflictException() {
        super("笔记已被更新，请先处理版本冲突");
    }
}
