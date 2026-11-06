package com.mike.workstation.exception;

/** 在线提示故障或容量限制独立于笔记保存链路。 */
public class NotePresenceException extends RuntimeException {
    private final boolean limited;

    public NotePresenceException(boolean limited) {
        super(limited ? "在线状态连接或页面数量已达上限，请稍后重试" : "在线状态暂不可用，笔记仍可正常保存");
        this.limited = limited;
    }

    public boolean limited() {
        return limited;
    }
}
