package io.github.abrar118.matbank.db;

import io.github.abrar118.matbank.domain.AccountType;
import io.github.abrar118.matbank.domain.LedgerEntry;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.TxKind;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class LedgerRepository {

    /**
     * Search criteria for one user's ledger. Null fields don't filter.
     *
     * @param from inclusive
     * @param to   exclusive
     */
    public record Query(long userId, AccountType account, TxKind.Category category, String text,
                        Instant from, Instant to, int limit) {
    }

    private static final String SELECT = """
            SELECT e.id, e.account_id, a.type AS account_type, e.kind, e.amount_cents, e.balance_after_cents,
                   e.counterparty, e.description, e.note, e.reference, e.created_at
              FROM ledger_entries e
              JOIN accounts a ON a.id = e.account_id
            """;

    public long insert(Connection c, long accountId, TxKind kind, Money amount, Money balanceAfter,
                       String counterparty, String description, String note, String reference, Instant at)
            throws SQLException {
        return Sql.insert(c, """
                        INSERT INTO ledger_entries (account_id, kind, amount_cents, balance_after_cents, counterparty,
                                                    description, note, reference, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                accountId, kind, amount.cents(), balanceAfter.cents(), counterparty, description, note, reference,
                at);
    }

    public List<LedgerEntry> recent(Connection c, long userId, int limit) throws SQLException {
        return Sql.list(c, SELECT + " WHERE a.user_id = ? ORDER BY e.created_at DESC, e.id DESC LIMIT ?",
                LedgerRepository::map, userId, limit);
    }

    public List<LedgerEntry> search(Connection c, Query q) throws SQLException {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE a.user_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(q.userId());
        if (q.account() != null) {
            sql.append(" AND a.type = ?");
            params.add(q.account());
        }
        if (q.category() != null) {
            List<TxKind> kinds = Arrays.stream(TxKind.values()).filter(k -> k.category() == q.category()).toList();
            sql.append(" AND e.kind IN (")
                    .append(kinds.stream().map(k -> "?").collect(Collectors.joining(", ")))
                    .append(")");
            params.addAll(kinds);
        }
        if (q.text() != null && !q.text().isBlank()) {
            String pattern = Sql.likeContains(q.text().strip());
            sql.append("""
                     AND (e.counterparty LIKE ? ESCAPE '\\'
                          OR e.description LIKE ? ESCAPE '\\'
                          OR e.note LIKE ? ESCAPE '\\'
                          OR e.reference LIKE ? ESCAPE '\\')""");
            for (int i = 0; i < 4; i++) {
                params.add(pattern);
            }
        }
        if (q.from() != null) {
            sql.append(" AND e.created_at >= ?");
            params.add(q.from());
        }
        if (q.to() != null) {
            sql.append(" AND e.created_at < ?");
            params.add(q.to());
        }
        sql.append(" ORDER BY e.created_at DESC, e.id DESC LIMIT ?");
        params.add(q.limit() > 0 ? q.limit() : 1000);
        return Sql.list(c, sql.toString(), LedgerRepository::map, params.toArray());
    }

    /** Entries for one account in {@code [from, to)}, oldest first, as a statement lists them. */
    public List<LedgerEntry> forAccount(Connection c, long accountId, Instant from, Instant to) throws SQLException {
        return Sql.list(c, SELECT + """
                         WHERE e.account_id = ? AND e.created_at >= ? AND e.created_at < ?
                         ORDER BY e.created_at, e.id""",
                LedgerRepository::map, accountId, from, to);
    }

    /** The account balance just before {@code instant}, or zero if the account had no activity yet. */
    public Money balanceBefore(Connection c, long accountId, Instant instant) throws SQLException {
        Optional<Long> cents = Sql.one(c, """
                        SELECT balance_after_cents FROM ledger_entries
                         WHERE account_id = ? AND created_at < ?
                         ORDER BY created_at DESC, id DESC LIMIT 1""",
                rs -> rs.getLong(1), accountId, instant);
        return Money.ofCents(cents.orElse(0L));
    }

    /** Every user's entries since {@code from}, for bank-wide statistics. */
    public List<LedgerEntry> allSince(Connection c, Instant from) throws SQLException {
        return Sql.list(c, SELECT + " WHERE e.created_at >= ? ORDER BY e.created_at", LedgerRepository::map, from);
    }

    public List<LedgerEntry> forUserSince(Connection c, long userId, Instant from) throws SQLException {
        return Sql.list(c, SELECT + " WHERE a.user_id = ? AND e.created_at >= ? ORDER BY e.created_at, e.id",
                LedgerRepository::map, userId, from);
    }

    static LedgerEntry map(ResultSet rs) throws SQLException {
        return new LedgerEntry(
                rs.getLong("id"),
                rs.getLong("account_id"),
                AccountType.valueOf(rs.getString("account_type")),
                TxKind.valueOf(rs.getString("kind")),
                Money.ofCents(rs.getLong("amount_cents")),
                Money.ofCents(rs.getLong("balance_after_cents")),
                rs.getString("counterparty"),
                rs.getString("description"),
                rs.getString("note"),
                rs.getString("reference"),
                Sql.instant(rs, "created_at"));
    }
}
