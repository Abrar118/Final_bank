package io.github.abrar118.matbank.db;

import io.github.abrar118.matbank.domain.AuditEvent;
import io.github.abrar118.matbank.domain.LoginEvent;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/** Login history and the admin audit trail. */
public final class ActivityRepository {

    public void recordLogin(Connection c, Long userId, String email, String userName, LoginEvent.Outcome outcome,
                            Instant at) throws SQLException {
        Sql.insert(c, "INSERT INTO login_events (user_id, email, user_name, outcome, created_at) VALUES (?, ?, ?, ?, ?)",
                userId, email, userName, outcome, at);
    }

    public List<LoginEvent> logins(Connection c, int limit) throws SQLException {
        return Sql.list(c, """
                        SELECT id, user_id, email, user_name, outcome, created_at
                          FROM login_events ORDER BY created_at DESC, id DESC LIMIT ?""",
                rs -> new LoginEvent(rs.getLong("id"), Sql.nullableLong(rs, "user_id"), rs.getString("email"),
                        rs.getString("user_name"), LoginEvent.Outcome.valueOf(rs.getString("outcome")),
                        Sql.instant(rs, "created_at")),
                limit);
    }

    public long loginsSince(Connection c, LoginEvent.Outcome outcome, Instant since) throws SQLException {
        return Sql.scalarLong(c, "SELECT COUNT(*) FROM login_events WHERE outcome = ? AND created_at >= ?",
                outcome, since);
    }

    public void audit(Connection c, Long actorId, String actorEmail, String action, String details, Instant at)
            throws SQLException {
        Sql.insert(c, "INSERT INTO audit_log (actor_id, actor_email, action, details, created_at) VALUES (?, ?, ?, ?, ?)",
                actorId, actorEmail, action, details, at);
    }

    public List<AuditEvent> auditLog(Connection c, int limit) throws SQLException {
        return Sql.list(c, """
                        SELECT id, actor_id, actor_email, action, details, created_at
                          FROM audit_log ORDER BY created_at DESC, id DESC LIMIT ?""",
                rs -> new AuditEvent(rs.getLong("id"), Sql.nullableLong(rs, "actor_id"), rs.getString("actor_email"),
                        rs.getString("action"), rs.getString("details"), Sql.instant(rs, "created_at")),
                limit);
    }
}
