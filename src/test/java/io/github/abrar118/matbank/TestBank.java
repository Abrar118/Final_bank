package io.github.abrar118.matbank;

import io.github.abrar118.matbank.db.Database;
import io.github.abrar118.matbank.db.LedgerRepository;
import io.github.abrar118.matbank.db.Migrator;
import io.github.abrar118.matbank.db.Sql;
import io.github.abrar118.matbank.db.UserRepository;
import io.github.abrar118.matbank.demo.MutableClock;
import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.AccountType;
import io.github.abrar118.matbank.domain.Gender;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.Role;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.AdminService;
import io.github.abrar118.matbank.service.ExchangeRateService;
import io.github.abrar118.matbank.service.PasswordHasher;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/** A fresh, migrated bank in a temp folder with a hand-driven clock and fast (cost 4) password hashing. */
public final class TestBank {

    public static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");
    public static final Instant START = Instant.parse("2026-03-15T04:00:00Z"); // 10:00 in Dhaka
    public static final String ADMIN_PASSWORD = "admin2022";
    public static final String ADMIN_PIN = "1111";
    public static final String CLIENT_PASSWORD = "secret123";

    public final Path dir;
    public final MutableClock clock;
    public final Database db;
    public final AppContext ctx;
    public final User admin;

    private TestBank(Path dir, ExchangeRateService.Fetcher fetcher) {
        this.dir = dir;
        this.clock = new MutableClock(START, DHAKA);
        this.db = new Database(dir.resolve("test.db"));
        new Migrator(db, clock).migrate();
        PasswordHasher hasher = new PasswordHasher(4);
        var rates = new ExchangeRateService(fetcher, dir.resolve("cache").resolve("rates.json"), clock);
        this.ctx = new AppContext(db, clock, hasher, rates);
        String passwordHash = hasher.hash(ADMIN_PASSWORD);
        String pinHash = hasher.hash(ADMIN_PIN);
        UserRepository users = new UserRepository();
        this.admin = db.inTransaction(c -> users.findById(c, users.insert(c, new UserRepository.NewUser(
                "admin@test.bank", passwordHash, pinHash, Role.ADMIN, "Test Admin", Gender.OTHER, null, null, null,
                null), clock.instant())).orElseThrow());
    }

    public static TestBank create(Path dir) {
        return new TestBank(dir, () -> {
            throw new IOException("offline in tests");
        });
    }

    public static TestBank create(Path dir, ExchangeRateService.Fetcher fetcher) {
        return new TestBank(dir, fetcher);
    }

    public User client(String name, String email, long checking, long savings) {
        return ctx.admin().createClient(admin, new AdminService.NewClient(name, email, CLIENT_PASSWORD, Gender.FEMALE,
                LocalDate.of(1999, 5, 1), null, null, "Dhaka", Money.of(checking), Money.of(savings)));
    }

    public Money balance(User user, AccountType type) {
        return ctx.banking().account(user.id(), type).balance();
    }

    public void advance(Duration duration) {
        clock.advance(duration);
    }

    public void assertLedgerConsistent() {
        assertLedgerConsistent(db);
    }

    /** Every account's balance must equal the sum of its ledger lines and the last running balance. */
    public static void assertLedgerConsistent(Database db) {
        db.read(c -> {
            var accounts = Sql.list(c, "SELECT id, user_id, type, number, balance_cents FROM accounts",
                    rs -> new Account(rs.getLong("id"), rs.getLong("user_id"),
                            AccountType.valueOf(rs.getString("type")), rs.getString("number"),
                            Money.ofCents(rs.getLong("balance_cents"))));
            LedgerRepository ledger = new LedgerRepository();
            for (Account account : accounts) {
                long sum = Sql.scalarLong(c, "SELECT COALESCE(SUM(amount_cents), 0) FROM ledger_entries WHERE account_id = ?",
                        account.id());
                assertThat(Money.ofCents(sum)).as("ledger sum of account %s", account.number())
                        .isEqualTo(account.balance());
                assertThat(ledger.balanceBefore(c, account.id(), Instant.parse("9999-12-31T00:00:00Z")))
                        .as("running balance of account %s", account.number())
                        .isEqualTo(account.balance());
            }
            return null;
        });
    }
}
