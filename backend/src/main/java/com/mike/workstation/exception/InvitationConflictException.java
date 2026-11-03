package com.mike.workstation.exception;

/** 同一邀请请求标识对应了不同业务内容。 */
public class InvitationConflictException extends RuntimeException {
    public InvitationConflictException(String message) {
        super(message);
    }
}
