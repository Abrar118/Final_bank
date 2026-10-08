package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.db.ActivityRepository;
import io.github.abrar118.matbank.db.Database;
import io.github.abrar118.matbank.db.UserRepository;
import io.github.abrar118.matbank.domain.Gender;
import io.github.abrar118.matbank.domain.User;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

public final class ProfileService {

    /** Profile pictures are stored in the database, so keep them small. */
    public static final int MAX_AVATAR_BYTES = 2 * 1024 * 1024;

    public record ProfileUpdate(String fullName, Gender gender, LocalDate birthDate, String phone, String facebook,
                                String address) {
    }

    private final Database db;
    private final UserRepository users;
    private final ActivityRepository activity;
    private final Clock clock;

    public ProfileService(Database db, UserRepository users, ActivityRepository activity, Clock clock) {
        this.db = db;
        this.users = users;
        this.activity = activity;
        this.clock = clock;
    }

    public User get(long userId) {
        return db.read(c -> users.findById(c, userId))
                .orElseThrow(() -> new BankException(BankException.Reason.ACCOUNT_NOT_FOUND, "User not found"));
    }

    public User update(long userId, ProfileUpdate update) {
        String name = Validation.maxLength(Validation.required(update.fullName(), "Full name"), 80, "Full name");
        if (update.gender() == null) {
            throw BankException.validation("Gender is required");
        }
        checkBirthDate(update.birthDate(), clock);
        return db.inTransaction(c -> {
            users.updateProfile(c, userId, name, update.gender(), update.birthDate(),
                    Validation.maxLength(Validation.optional(update.phone()), 30, "Phone"),
                    Validation.maxLength(Validation.optional(update.facebook()), 120, "Facebook"),
                    Validation.maxLength(Validation.optional(update.address()), 200, "Address"));
            User user = users.findById(c, userId).orElseThrow();
            activity.audit(c, userId, user.email(), "PROFILE_UPDATED", "Updated own profile", clock.instant());
            return user;
        });
    }

    public Optional<byte[]> avatar(long userId) {
        return db.read(c -> users.avatar(c, userId));
    }

    /** @param image PNG or JPEG bytes, or {@code null} to remove the picture */
    public void setAvatar(long userId, byte[] image) {
        if (image != null && image.length > MAX_AVATAR_BYTES) {
            throw BankException.validation("Pictures must be under 2 MB");
        }
        db.write(c -> users.updateAvatar(c, userId, image));
    }

    static void checkBirthDate(LocalDate birthDate, Clock clock) {
        if (birthDate == null) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        if (birthDate.isAfter(today.minusYears(13))) {
            throw BankException.validation("Clients must be at least 13 years old");
        }
        if (birthDate.isBefore(today.minusYears(120))) {
            throw BankException.validation("Check the date of birth");
        }
    }
}
