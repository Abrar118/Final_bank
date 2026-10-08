package io.github.abrar118.matbank.demo;

import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.TestBank;
import io.github.abrar118.matbank.db.Database;
import io.github.abrar118.matbank.db.Migrator;
import io.github.abrar118.matbank.domain.Role;
import io.github.abrar118.matbank.service.AuthService;
import io.github.abrar118.matbank.service.ExchangeRateService;
import io.github.abrar118.matbank.service.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DemoSeederTest {

    @TempDir
    Path dir;

    @Test
    void seedsAConsistentBankOnceAndTheDemoLoginsWork() {
        MutableClock clock = new MutableClock(TestBank.START, TestBank.DHAKA);
        Database db = new Database(dir.resolve("demo.db"));
        new Migrator(db, clock).migrate();
        AppContext ctx = new AppContext(db, clock, new PasswordHasher(4),
                new ExchangeRateService(() -> {
                    throw new IOException("offline");
                }, null, clock));

        DemoSeeder seeder = new DemoSeeder(ctx);
        assertThat(seeder.seedIfEmpty()).isTrue();
        assertThat(seeder.seedIfEmpty()).isFalse();

        for (DemoSeeder.DemoLogin login : DemoSeeder.LOGINS) {
            assertThat(ctx.auth().login(login.email(), login.password(), login.role(), login.pin()))
                    .as(login.label())
                    .isInstanceOf(AuthService.LoginResult.Success.class);
        }

        var admin = ((AuthService.LoginResult.Success) ctx.auth().login(DemoSeeder.ADMIN_EMAIL,
                DemoSeeder.ADMIN_PASSWORD, Role.ADMIN, DemoSeeder.ADMIN_PIN)).user();
        var clients = ctx.admin().clients(admin, "");
        assertThat(clients).hasSize(5);

        var rahim = clients.stream().filter(u -> u.email().startsWith("rahim")).findFirst().orElseThrow();
        assertThat(ctx.ledger().recent(rahim.id(), 500)).hasSizeGreaterThan(40);
        assertThat(ctx.recurring().list(rahim.id())).hasSize(2)
                .anySatisfy(p -> assertThat(p.active()).isFalse())
                .anySatisfy(p -> assertThat(p.runsCompleted()).isGreaterThanOrEqualTo(4));
        assertThat(ctx.support().messages(admin)).hasSize(3);
        assertThat(ctx.admin().loginActivity(admin, 1000)).hasSizeGreaterThan(50);

        // Every balance must match its ledger after months of simulated activity.
        TestBank.assertLedgerConsistent(db);
    }
}
