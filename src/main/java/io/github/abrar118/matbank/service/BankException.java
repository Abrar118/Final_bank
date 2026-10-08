package io.github.abrar118.matbank.service;

/** A business rule was violated. The message is safe to show to the user as-is. */
public class BankException extends RuntimeException {

    public enum Reason {
        VALIDATION,
        INSUFFICIENT_FUNDS,
        RECIPIENT_NOT_FOUND,
        ACCOUNT_NOT_FOUND,
        NOT_ALLOWED,
        DUPLICATE
    }

    private final Reason reason;

    public BankException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public static BankException validation(String message) {
        return new BankException(Reason.VALIDATION, message);
    }

    public Reason reason() {
        return reason;
    }
}
