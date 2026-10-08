package io.github.abrar118.matbank.db;

import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.AccountType;
import io.github.abrar118.matbank.domain.Money;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class AccountRepository {

    private static final String COLUMNS = "id, user_id, type, number, balance_cents";

    public long insert(Connection c, long userId, AccountType type, String number, Instant now) throws SQLException {
        return Sql.insert(c, "INSERT INTO accounts (user_id, type, number, created_at) VALUES (?, ?, ?, ?)",
                userId, type, number, now);
    }

    public List<Account> findByUser(Connection c, long userId) throws SQLException {
        return Sql.list(c, "SELECT " + COLUMNS + " FROM accounts WHERE user_id = ? ORDER BY type",
                AccountRepository::map, userId);
    }

    public Optional<Account> find(Connection c, long userId, AccountType type) throws SQLException {
        return Sql.one(c, "SELECT " + COLUMNS + " FROM accounts WHERE user_id = ? AND type = ?",
                AccountRepository::map, userId, type);
    }

    public boolean numberExists(Connection c, String number) throws SQLException {
        return Sql.scalarLong(c, "SELECT COUNT(*) FROM accounts WHERE number = ?", number) > 0;
    }

    /**
     * Subtracts {@code amount} only if the balance covers it, in a single statement so the check and the
     * update can't be split by another writer.
     *
     * @return the new balance, or empty if funds were insufficient
     */
    public Optional<Money> debit(Connection c, long accountId, Money amount) throws SQLException {
        int updated = Sql.update(c,
                "UPDATE accounts SET balance_cents = balance_cents - ? WHERE id = ? AND balance_cents >= ?",
                amount.cents(), accountId, amount.cents());
        return updated == 1 ? Optional.of(balance(c, accountId)) : Optional.empty();
    }

    /** @return the new balance */
    public Money credit(Connection c, long accountId, Money amount) throws SQLException {
        int updated = Sql.update(c, "UPDATE accounts SET balance_cents = balance_cents + ? WHERE id = ?",
                amount.cents(), accountId);
        if (updated != 1) {
            throw new SQLException("Account " + accountId + " not found");
        }
        return balance(c, accountId);
    }

    public Money balance(Connection c, long accountId) throws SQLException {
        return Money.ofCents(Sql.scalarLong(c, "SELECT balance_cents FROM accounts WHERE id = ?", accountId));
    }

    /** Sum of every client balance: the money the bank is holding. */
    public Money totalHoldings(Connection c) throws SQLException {
        return Money.ofCents(Sql.scalarLong(c, "SELECT COALESCE(SUM(balance_cents), 0) FROM accounts"));
    }

    static Account map(ResultSet rs) throws SQLException {
        return new Account(
                rs.getLong("id"),
                rs.getLong("user_id"),
                AccountType.valueOf(rs.getString("type")),
                rs.getString("number"),
                Money.ofCents(rs.getLong("balance_cents")));
    }
}
