package io.github.abrar118.matbank.domain;

import java.time.Instant;
import java.time.LocalDate;

/** A client or admin. Credentials and the avatar image are deliberately not part of this record. */
public record User(
        long id,
        String email,
        String fullName,
        Role role,
        Gender gender,
        LocalDate birthDate,
        String phone,
        String facebook,
        String address,
        Instant createdAt) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public String firstName() {
        int space = fullName.indexOf(' ');
        return space < 0 ? fullName : fullName.substring(0, space);
    }

    /** Up to two initials for avatar placeholders, e.g. "Abrar Mahir Esam" → "AE". */
    public String initials() {
        String[] parts = fullName.strip().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return "?";
        }
        String first = parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase();
    }
}
