package com.mike.workstation.exception;

/** 防止单个用户或节点持有过量长连接。 */
public class NotificationStreamLimitException extends RuntimeException {
    public NotificationStreamLimitException() {
        super("通知连接数已达上限，请稍后重试");
    }
}
