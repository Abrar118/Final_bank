package io.github.abrar118.matbank.db;

import org.sqlite.SQLiteConfig;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hands out SQLite connections to a single database file.
 *
 * <p>Each unit of work opens its own short-lived connection. SQLite opens are cheap, and this keeps the
 * JavaFX thread and the background scheduler from sharing a connection. Writes run in
 * {@code BEGIN IMMEDIATE} transactions so concurrent writers queue on the busy timeout instead of
 * deadlocking.
 */
public final class Database {

    @FunctionalInterface
    public interface Work<T> {
        T apply(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface VoidWork {
        void apply(Connection connection) throws SQLException;
    }

    private final Path file;
    private final String url;
    private final SQLiteConfig config;

    public Database(Path file) {
        this.file = file;
        this.url = "jdbc:sqlite:" + file.toAbsolutePath();
        this.config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setBusyTimeout(10_000);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
    }

    public Path file() {
        return file;
    }

    public Connection open() throws SQLException {
        return config.createConnection(url);
    }

    /** Runs read-only work in auto-commit mode. */
    public <T> T read(Work<T> work) {
        try (Connection c = open()) {
            return work.apply(c);
        } catch (SQLException e) {
            throw new DataAccessException("Database read failed", e);
        }
    }

    /** Runs work in a single transaction: commits on success, rolls back on any exception. */
    public <T> T inTransaction(Work<T> work) {
        try (Connection c = open()) {
            c.setAutoCommit(false);
            try {
                T result = work.apply(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException | Error e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Database write failed", e);
        }
    }

    /** Like {@link #inTransaction(Work)} for work that returns nothing. */
    public void write(VoidWork work) {
        inTransaction(c -> {
            work.apply(c);
            return null;
        });
    }
}
