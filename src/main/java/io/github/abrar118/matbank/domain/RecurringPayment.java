package io.github.abrar118.matbank.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A standing order: send {@code amount} to {@code recipientEmail} on a schedule.
 *
 * @param runsCompleted occurrences already processed (paid or skipped); the next one is occurrence #runsCompleted
 * @param lastResult    human-readable outcome of the most recent run, or {@code null} before the first run
 */
public record RecurringPayment(
        long id,
        long userId,
        AccountType fromAccount,
        String recipientEmail,
        Money amount,
        String note,
        Frequency frequency,
        LocalDate startDate,
        int runsCompleted,
        LocalDate nextRun,
        boolean active,
        String lastResult,
        Instant createdAt) {
}
