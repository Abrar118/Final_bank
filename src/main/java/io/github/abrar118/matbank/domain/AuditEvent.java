package io.github.abrar118.matbank.domain;

import java.time.Instant;

/** A security-relevant action, such as an admin creating a client or a password change. */
public record AuditEvent(long id, Long actorId, String actorEmail, String action, String details, Instant createdAt) {
}
