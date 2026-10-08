package io.github.abrar118.matbank.db;

import io.github.abrar118.matbank.domain.Gender;
import io.github.abrar118.matbank.domain.Role;
import io.github.abrar118.matbank.domain.User;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class UserRepository {

    /** Everything needed to check a login attempt. Never leaves the service layer. */
    public record Credentials(long userId, Role role, String passwordHash, String pinHash,
                              int failedLogins, Instant lockedUntil) {
    }

    public record NewUser(String email, String passwordHash, String pinHash, Role role, String fullName,
                          Gender gender, LocalDate birthDate, String phone, String facebook, String address) {
    }

    private static final String COLUMNS =
            "id, email, full_name, role, gender, birth_date, phone, facebook, address, created_at";

    public long insert(Connection c, NewUser u, Instant now) throws SQLException {
        return Sql.insert(c, """
                        INSERT INTO users (email, password_hash, pin_hash, role, full_name, gender, birth_date,
                                           phone, facebook, address, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                u.email(), u.passwordHash(), u.pinHash(), u.role(), u.fullName(), u.gender(), u.birthDate(),
                u.phone(), u.facebook(), u.address(), now);
    }

    public Optional<User> findById(Connection c, long id) throws SQLException {
        return Sql.one(c, "SELECT " + COLUMNS + " FROM users WHERE id = ?", UserRepository::map, id);
    }

    public Optional<User> findByEmail(Connection c, String email) throws SQLException {
        return Sql.one(c, "SELECT " + COLUMNS + " FROM users WHERE email = ?", UserRepository::map, email.strip());
    }

    public boolean emailExists(Connection c, String email) throws SQLException {
        return Sql.scalarLong(c, "SELECT COUNT(*) FROM users WHERE email = ?", email.strip()) > 0;
    }

    private static final String CREDENTIAL_COLUMNS =
            "id, role, password_hash, pin_hash, failed_logins, locked_until";

    public Optional<Credentials> credentials(Connection c, String email) throws SQLException {
        return Sql.one(c, "SELECT " + CREDENTIAL_COLUMNS + " FROM users WHERE email = ?",
                UserRepository::mapCredentials, email.strip());
    }

    public Optional<Credentials> credentials(Connection c, long userId) throws SQLException {
        return Sql.one(c, "SELECT " + CREDENTIAL_COLUMNS + " FROM users WHERE id = ?",
                UserRepository::mapCredentials, userId);
    }

    private static Credentials mapCredentials(ResultSet rs) throws SQLException {
        return new Credentials(rs.getLong("id"), Role.valueOf(rs.getString("role")),
                rs.getString("password_hash"), rs.getString("pin_hash"),
                rs.getInt("failed_logins"), Sql.instant(rs, "locked_until"));
    }

    public void updateLoginState(Connection c, long userId, int failedLogins, Instant lockedUntil) throws SQLException {
        Sql.update(c, "UPDATE users SET failed_logins = ?, locked_until = ? WHERE id = ?",
                failedLogins, lockedUntil, userId);
    }

    public void updatePasswordHash(Connection c, long userId, String hash) throws SQLException {
        Sql.update(c, "UPDATE users SET password_hash = ?, failed_logins = 0, locked_until = NULL WHERE id = ?",
                hash, userId);
    }

    public void updatePinHash(Connection c, long userId, String hash) throws SQLException {
        Sql.update(c, "UPDATE users SET pin_hash = ? WHERE id = ?", hash, userId);
    }

    public void updateProfile(Connection c, long userId, String fullName, Gender gender, LocalDate birthDate,
                              String phone, String facebook, String address) throws SQLException {
        Sql.update(c, """
                        UPDATE users SET full_name = ?, gender = ?, birth_date = ?, phone = ?, facebook = ?, address = ?
                        WHERE id = ?""",
                fullName, gender, birthDate, phone, facebook, address, userId);
    }

    public Optional<byte[]> avatar(Connection c, long userId) throws SQLException {
        return Sql.one(c, "SELECT avatar FROM users WHERE id = ?", rs -> rs.getBytes(1), userId);
    }

    public void updateAvatar(Connection c, long userId, byte[] image) throws SQLException {
        Sql.update(c, "UPDATE users SET avatar = ? WHERE id = ?", image, userId);
    }

    /** Clients whose name or email contains {@code search} (case-insensitive); all clients if blank. */
    public List<User> findClients(Connection c, String search) throws SQLException {
        String pattern = Sql.likeContains(search == null ? "" : search.strip());
        return Sql.list(c, "SELECT " + COLUMNS + """
                         FROM users
                        WHERE role = 'CLIENT'
                          AND (full_name LIKE ? ESCAPE '\\' OR email LIKE ? ESCAPE '\\')
                        ORDER BY full_name COLLATE NOCASE""",
                UserRepository::map, pattern, pattern);
    }

    public List<User> findByRole(Connection c, Role role) throws SQLException {
        return Sql.list(c, "SELECT " + COLUMNS + " FROM users WHERE role = ? ORDER BY id", UserRepository::map, role);
    }

    public long count(Connection c, Role role) throws SQLException {
        return Sql.scalarLong(c, "SELECT COUNT(*) FROM users WHERE role = ?", role);
    }

    public long countAll(Connection c) throws SQLException {
        return Sql.scalarLong(c, "SELECT COUNT(*) FROM users");
    }

    public void delete(Connection c, long userId) throws SQLException {
        Sql.update(c, "DELETE FROM users WHERE id = ?", userId);
    }

    static User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("full_name"),
                Role.valueOf(rs.getString("role")),
                Gender.valueOf(rs.getString("gender")),
                Sql.date(rs, "birth_date"),
                rs.getString("phone"),
                rs.getString("facebook"),
                rs.getString("address"),
                Sql.instant(rs, "created_at"));
    }
}
