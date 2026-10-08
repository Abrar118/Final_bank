package io.github.abrar118.matbank.db;

import io.github.abrar118.matbank.domain.AccountType;
import io.github.abrar118.matbank.domain.Frequency;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.RecurringPayment;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public final class RecurringPaymentRepository {

    private static final String COLUMNS = """
            id, user_id, from_account, recipient_email, amount_cents, note, frequency, start_date,
            runs_completed, next_run, active, last_result, created_at""";

    public long insert(Connection c, long userId, AccountType from, String recipientEmail, Money amount, String note,
                       Frequency frequency, LocalDate startDate, Instant now) throws SQLException {
        return Sql.insert(c, """
                        INSERT INTO recurring_payments (user_id, from_account, recipient_email, amount_cents, note,
                                                        frequency, start_date, next_run, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                userId, from, recipientEmail, amount.cents(), note, frequency, startDate, startDate, now);
    }

    public List<RecurringPayment> forUser(Connection c, long userId) throws SQLException {
        return Sql.list(c, "SELECT " + COLUMNS + " FROM recurring_payments WHERE user_id = ? ORDER BY active DESC, next_run",
                RecurringPaymentRepository::map, userId);
    }

    public Optional<RecurringPayment> find(Connection c, long id) throws SQLException {
        return Sql.one(c, "SELECT " + COLUMNS + " FROM recurring_payments WHERE id = ?",
                RecurringPaymentRepository::map, id);
    }

    public List<RecurringPayment> due(Connection c, LocalDate today) throws SQLException {
        return Sql.list(c, "SELECT " + COLUMNS + " FROM recurring_payments WHERE active = 1 AND next_run <= ? ORDER BY next_run, id",
                RecurringPaymentRepository::map, today);
    }

    public void recordRun(Connection c, long id, int runsCompleted, LocalDate nextRun, String result)
            throws SQLException {
        Sql.update(c, "UPDATE recurring_payments SET runs_completed = ?, next_run = ?, last_result = ? WHERE id = ?",
                runsCompleted, nextRun, result, id);
    }

    public void setActive(Connection c, long id, boolean active, String result) throws SQLException {
        Sql.update(c, "UPDATE recurring_payments SET active = ?, last_result = COALESCE(?, last_result) WHERE id = ?",
                active, result, id);
    }

    /** Moves a resumed payment's next run forward without paying the occurrences missed while paused. */
    public void reschedule(Connection c, long id, int runsCompleted, LocalDate nextRun) throws SQLException {
        Sql.update(c, "UPDATE recurring_payments SET runs_completed = ?, next_run = ? WHERE id = ?",
                runsCompleted, nextRun, id);
    }

    public void delete(Connection c, long id) throws SQLException {
        Sql.update(c, "DELETE FROM recurring_payments WHERE id = ?", id);
    }

    static RecurringPayment map(ResultSet rs) throws SQLException {
        return new RecurringPayment(
                rs.getLong("id"),
                rs.getLong("user_id"),
                AccountType.valueOf(rs.getString("from_account")),
                rs.getString("recipient_email"),
                Money.ofCents(rs.getLong("amount_cents")),
                rs.getString("note"),
                Frequency.valueOf(rs.getString("frequency")),
                Sql.date(rs, "start_date"),
                rs.getInt("runs_completed"),
                Sql.date(rs, "next_run"),
                rs.getInt("active") == 1,
                rs.getString("last_result"),
                Sql.instant(rs, "created_at"));
    }
}
