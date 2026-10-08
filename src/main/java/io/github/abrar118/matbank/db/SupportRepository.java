package io.github.abrar118.matbank.db;

import io.github.abrar118.matbank.domain.Feedback;
import io.github.abrar118.matbank.domain.SupportMessage;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/** Help-center messages and product feedback. */
public final class SupportRepository {

    public long insertMessage(Connection c, Long senderId, String name, String email, String body, Instant at)
            throws SQLException {
        return Sql.insert(c, """
                        INSERT INTO support_messages (sender_id, sender_name, sender_email, body, created_at)
                        VALUES (?, ?, ?, ?, ?)""",
                senderId, name, email, body, at);
    }

    public List<SupportMessage> messages(Connection c) throws SQLException {
        return Sql.list(c, """
                        SELECT id, sender_id, sender_name, sender_email, body, is_read, created_at
                          FROM support_messages ORDER BY created_at DESC, id DESC""",
                rs -> new SupportMessage(rs.getLong("id"), Sql.nullableLong(rs, "sender_id"),
                        rs.getString("sender_name"), rs.getString("sender_email"), rs.getString("body"),
                        rs.getInt("is_read") == 1, Sql.instant(rs, "created_at")));
    }

    public void markRead(Connection c, long messageId, boolean read) throws SQLException {
        Sql.update(c, "UPDATE support_messages SET is_read = ? WHERE id = ?", read, messageId);
    }

    public long unreadCount(Connection c) throws SQLException {
        return Sql.scalarLong(c, "SELECT COUNT(*) FROM support_messages WHERE is_read = 0");
    }

    public long insertFeedback(Connection c, Long userId, String userName, int rating, String body, Instant at)
            throws SQLException {
        return Sql.insert(c, "INSERT INTO feedback (user_id, user_name, rating, body, created_at) VALUES (?, ?, ?, ?, ?)",
                userId, userName, rating, body, at);
    }

    public List<Feedback> feedback(Connection c) throws SQLException {
        return Sql.list(c, """
                        SELECT id, user_id, user_name, rating, body, created_at
                          FROM feedback ORDER BY created_at DESC, id DESC""",
                rs -> new Feedback(rs.getLong("id"), Sql.nullableLong(rs, "user_id"), rs.getString("user_name"),
                        rs.getInt("rating"), rs.getString("body"), Sql.instant(rs, "created_at")));
    }
}
