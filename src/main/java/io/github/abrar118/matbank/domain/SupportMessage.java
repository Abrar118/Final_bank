package io.github.abrar118.matbank.domain;

import java.time.Instant;

/** A message sent to the bank's admins from the help center. The sender may be a visitor without an account. */
public record SupportMessage(
        long id,
        Long senderId,
        String senderName,
        String senderEmail,
        String body,
        boolean read,
        Instant createdAt) {
}
