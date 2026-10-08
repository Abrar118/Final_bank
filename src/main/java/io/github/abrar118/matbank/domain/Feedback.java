package io.github.abrar118.matbank.domain;

import java.time.Instant;

public record Feedback(long id, Long userId, String userName, int rating, String body, Instant createdAt) {
}
