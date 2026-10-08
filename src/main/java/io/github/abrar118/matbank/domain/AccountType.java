package io.github.abrar118.matbank.domain;

public enum AccountType {
    CHECKING("Checking"),
    SAVINGS("Savings");

    private final String label;

    AccountType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public AccountType other() {
        return this == CHECKING ? SAVINGS : CHECKING;
    }

    @Override
    public String toString() {
        return label;
    }
}
