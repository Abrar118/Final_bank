package io.github.abrar118.matbank.db;

import io.github.abrar118.matbank.TestBank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigratorTest {

    @TempDir
    Path dir;

    @Test
    void createsSchemaAndIsIdempotent() {
        Database db = new Database(dir.resolve("m.db"));
        Migrator migrator = new Migrator(db, Clock.systemUTC());

        assertThat(migrator.migrate()).isEqualTo(Migrator.MIGRATIONS.size());
        assertThat(migrator.migrate()).isEqualTo(Migrator.MIGRATIONS.size());

        List<String> tables = db.read(c -> Sql.list(c,
                "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name", rs -> rs.getString(1)));
        assertThat(tables).contains("users", "accounts", "ledger_entries", "support_messages", "feedback",
                "login_events", "audit_log", "recurring_payments", "schema_version");
    }

    @Test
    void enforcesForeignKeysAndNonNegativeBalances() {
        TestBank bank = TestBank.create(dir);
        var client = bank.client("Ayesha Siddiqua", "ayesha@test.bank", 100, 0);

        Integer violations = bank.db.read(c -> {
            int failures = 0;
            try {
                Sql.update(c, "UPDATE accounts SET balance_cents = -1 WHERE user_id = ?", client.id());
            } catch (java.sql.SQLException e) {
                failures++;
            }
            try {
                Sql.insert(c, "INSERT INTO accounts (user_id, type, number, created_at) VALUES (9999, 'CHECKING', 'x', 'now')");
            } catch (java.sql.SQLException e) {
                failures++;
            }
            return failures;
        });
        assertThat(violations).isEqualTo(2);
    }

    @Test
    void splitsStatementsAndDropsComments() {
        String script = """
                -- comment
                CREATE TABLE a (id INTEGER);

                CREATE TABLE b (
                    id INTEGER -- trailing comments stay inside the statement
                );
                """;
        assertThat(Migrator.statements(script)).hasSize(2)
                .first().isEqualTo("CREATE TABLE a (id INTEGER)");
    }
}
