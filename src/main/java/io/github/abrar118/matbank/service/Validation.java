package io.github.abrar118.matbank.service;

import java.util.regex.Pattern;

/** Input rules shared by sign-up, profile edits and admin tools. Each method throws {@link BankException}. */
public final class Validation {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");
    private static final Pattern PIN = Pattern.compile("^\\d{4,6}$");

    public static final int MIN_PASSWORD_LENGTH = 8;
    /** bcrypt only uses the first 72 bytes; keep well under that for multi-byte characters. */
    public static final int MAX_PASSWORD_LENGTH = 64;

    private Validation() {
    }

    public static String email(String email) {
        String trimmed = email == null ? "" : email.strip();
        if (!EMAIL.matcher(trimmed).matches()) {
            throw BankException.validation("Enter a valid email address");
        }
        return trimmed.toLowerCase();
    }

    public static void password(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw BankException.validation("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw BankException.validation("Password must be at most " + MAX_PASSWORD_LENGTH + " characters");
        }
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit)) {
            throw BankException.validation("Password must contain both letters and numbers");
        }
    }

    public static void pin(String pin) {
        if (pin == null || !PIN.matcher(pin).matches()) {
            throw BankException.validation("PIN must be 4 to 6 digits");
        }
    }

    public static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw BankException.validation(field + " is required");
        }
        return value.strip();
    }

    /** Trims optional text and turns blanks into {@code null}. */
    public static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public static String maxLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw BankException.validation(field + " must be at most " + max + " characters");
        }
        return value;
    }
}
