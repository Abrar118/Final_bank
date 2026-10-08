package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.db.ActivityRepository;
import io.github.abrar118.matbank.db.Database;
import io.github.abrar118.matbank.db.UserRepository;
import io.github.abrar118.matbank.domain.LoginEvent.Outcome;
import io.github.abrar118.matbank.domain.Role;
import io.github.abrar118.matbank.domain.User;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Sign-in, lockout and credential changes.
 *
 * <p>After {@link #MAX_FAILED_ATTEMPTS} wrong passwords in a row an account is locked for
 * {@link #LOCK_DURATION}. Error messages never say whether the email or the password was wrong.
 */
public final class AuthService {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(5);

    public sealed interface LoginResult {
        record Success(User user) implements LoginResult {
        }

        record Failure(String message, int attemptsLeft) implements LoginResult {
        }

        record Locked(Instant until) implements LoginResult {
        }
    }

    private static final String GENERIC_FAILURE = "Incorrect email or password";
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final Database db;
    private final UserRepository users;
    private final ActivityRepository activity;
    private final PasswordHasher hasher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthService(Database db, UserRepository users, ActivityRepository activity, PasswordHasher hasher,
                       Clock clock) {
        this.db = db;
        this.users = users;
        this.activity = activity;
        this.hasher = hasher;
        this.clock = clock;
    }

    /**
     * Checks credentials for the given role. Admins must also supply their PIN.
     * Runs a bcrypt verification, so call it off the JavaFX thread.
     */
    public LoginResult login(String email, String password, Role role, String pin) {
        String normalized = email == null ? "" : email.strip().toLowerCase();
        Instant now = clock.instant();

        Optional<UserRepository.Credentials> found = db.read(c -> users.credentials(c, normalized));
        if (found.isEmpty() || found.get().role() != role) {
            hasher.verifyDummy(password);
            // A client trying the admin tab (or vice versa) is a wrong-credentials attempt, but doesn't count
            // toward the lockout.
            Long userId = found.map(UserRepository.Credentials::userId).orElse(null);
            record(userId, normalized, found.isEmpty() ? Outcome.UNKNOWN_USER : Outcome.WRONG_CREDENTIALS, now);
            return new LoginResult.Failure(GENERIC_FAILURE, -1);
        }

        UserRepository.Credentials creds = found.get();
        if (creds.lockedUntil() != null && creds.lockedUntil().isAfter(now)) {
            record(creds.userId(), normalized, Outcome.LOCKED, now);
            return new LoginResult.Locked(creds.lockedUntil());
        }

        boolean ok = hasher.verify(password, creds.passwordHash())
                && (role != Role.ADMIN || hasher.verify(pin, creds.pinHash()));

        if (!ok) {
            int failures = creds.failedLogins() + 1;
            if (failures >= MAX_FAILED_ATTEMPTS) {
                Instant until = now.plus(LOCK_DURATION);
                db.write(c -> {
                    users.updateLoginState(c, creds.userId(), 0, until);
                    activity.recordLogin(c, creds.userId(), normalized, null, Outcome.WRONG_CREDENTIALS, now);
                    activity.audit(c, creds.userId(), normalized, "ACCOUNT_LOCKED",
                            MAX_FAILED_ATTEMPTS + " failed sign-in attempts", now);
                });
                return new LoginResult.Locked(until);
            }
            db.write(c -> {
                users.updateLoginState(c, creds.userId(), failures, null);
                activity.recordLogin(c, creds.userId(), normalized, null, Outcome.WRONG_CREDENTIALS, now);
            });
            return new LoginResult.Failure(GENERIC_FAILURE, MAX_FAILED_ATTEMPTS - failures);
        }

        User user = db.inTransaction(c -> {
            users.updateLoginState(c, creds.userId(), 0, null);
            User u = users.findById(c, creds.userId()).orElseThrow();
            activity.recordLogin(c, u.id(), u.email(), u.fullName(), Outcome.SUCCESS, now);
            return u;
        });
        return new LoginResult.Success(user);
    }

    /** Changes the signed-in user's own password after checking the current one. */
    public void changePassword(long userId, String currentPassword, String newPassword) {
        Validation.password(newPassword);
        UserRepository.Credentials creds = db.read(c -> users.credentials(c, userId))
                .orElseThrow(() -> new BankException(BankException.Reason.ACCOUNT_NOT_FOUND, "User not found"));
        if (!hasher.verify(currentPassword, creds.passwordHash())) {
            throw BankException.validation("Current password is incorrect");
        }
        if (hasher.verify(newPassword, creds.passwordHash())) {
            throw BankException.validation("New password must be different from the current one");
        }
        String hash = hasher.hash(newPassword);
        db.write(c -> {
            users.updatePasswordHash(c, userId, hash);
            String email = users.findById(c, userId).map(User::email).orElse(null);
            activity.audit(c, userId, email, "PASSWORD_CHANGED", "Changed own password", clock.instant());
        });
    }

    /** Changes an admin's sign-in PIN after checking their password. */
    public void changePin(long adminId, String currentPassword, String newPin) {
        Validation.pin(newPin);
        UserRepository.Credentials creds = db.read(c -> users.credentials(c, adminId))
                .filter(cr -> cr.role() == Role.ADMIN)
                .orElseThrow(() -> new BankException(BankException.Reason.NOT_ALLOWED, "Only admins have a PIN"));
        if (!hasher.verify(currentPassword, creds.passwordHash())) {
            throw BankException.validation("Password is incorrect");
        }
        String hash = hasher.hash(newPin);
        db.write(c -> {
            users.updatePinHash(c, adminId, hash);
            String email = users.findById(c, adminId).map(User::email).orElse(null);
            activity.audit(c, adminId, email, "PIN_CHANGED", "Changed own sign-in PIN", clock.instant());
        });
    }

    /**
     * Replaces the email-OTP flow from 2022: an admin issues a temporary password, which is shown once and
     * handed to the client, and the lockout is cleared.
     *
     * @return the temporary password
     */
    public String resetClientPassword(User admin, long clientId) {
        requireAdmin(admin);
        User client = db.read(c -> users.findById(c, clientId))
                .filter(u -> u.role() == Role.CLIENT)
                .orElseThrow(() -> new BankException(BankException.Reason.ACCOUNT_NOT_FOUND, "Client not found"));
        String temporary = temporaryPassword();
        String hash = hasher.hash(temporary);
        db.write(c -> {
            users.updatePasswordHash(c, clientId, hash);
            activity.audit(c, admin.id(), admin.email(), "PASSWORD_RESET",
                    "Issued a temporary password for " + client.email(), clock.instant());
        });
        return temporary;
    }

    String temporaryPassword() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        }
        // Guarantee the result passes our own password rules.
        sb.append(random.nextInt(10));
        sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(24)));
        return sb.toString();
    }

    static void requireAdmin(User user) {
        if (user == null || !user.isAdmin()) {
            throw new BankException(BankException.Reason.NOT_ALLOWED, "Only admins can do that");
        }
    }

    private void record(Long userId, String email, Outcome outcome, Instant at) {
        db.write(c -> activity.recordLogin(c, userId, email.isEmpty() ? "(blank)" : email, null, outcome, at));
    }
}
