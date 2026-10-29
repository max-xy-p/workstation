package com.mike.workstation.mail;

import java.time.Instant;

public record MailTask(String id, String caller, String fingerprint, String payload, String status,
                       int attempts, Instant expiresAt, String leaseToken, String providerId, String errorCode) {
    public MailStatus view() {
        return new MailStatus(id, status, attempts, providerId, errorCode, expiresAt);
    }

    @Override
    public String toString() {
        return "MailTask[id=" + id + ", status=" + status + "]";
    }
}
