package io.github.abrar118.matbank.domain;

import java.time.LocalDate;

public enum Frequency {
    DAILY("Every day"),
    WEEKLY("Every week"),
    MONTHLY("Every month");

    private final String label;

    Frequency(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * The date of the n-th occurrence (0-based) counted from {@code start}. Computing from the start date
     * instead of the previous run keeps a payment set up on the 31st from drifting to the 28th after February.
     */
    public LocalDate occurrence(LocalDate start, long n) {
        return switch (this) {
            case DAILY -> start.plusDays(n);
            case WEEKLY -> start.plusWeeks(n);
            case MONTHLY -> start.plusMonths(n);
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
