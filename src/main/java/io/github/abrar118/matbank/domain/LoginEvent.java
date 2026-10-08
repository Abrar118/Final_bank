package io.github.abrar118.matbank.domain;

import java.time.Instant;

public record LoginEvent(long id, Long userId, String email, String userName, Outcome outcome, Instant createdAt) {

    public enum Outcome {
        SUCCESS("Signed in"),
        WRONG_CREDENTIALS("Wrong credentials"),
        UNKNOWN_USER("Unknown account"),
        LOCKED("Blocked: account locked");

        private final String label;

        Outcome(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
