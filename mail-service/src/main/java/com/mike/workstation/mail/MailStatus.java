package com.mike.workstation.mail;

import java.time.Instant;

public record MailStatus(String id, String status, int attempts, String providerId,
                         String errorCode, Instant expiresAt) { }
