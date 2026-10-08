package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.TestBank;
import io.github.abrar118.matbank.db.Sql;
import io.github.abrar118.matbank.domain.LoginEvent;
import io.github.abrar118.matbank.domain.Role;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.AuthService.LoginResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTest {

    @TempDir
    Path dir;

    TestBank bank;
    AuthService auth;
    User client;

    @BeforeEach
    void setUp() {
        bank = TestBank.create(dir);
        auth = bank.ctx.auth();
        client = bank.client("Mehmil Khan", "mehmil@test.bank", 1_000, 5_000);
    }

    @Test
    void storesBcryptHashesNotPlainText() {
        String hash = bank.db.read(c -> Sql.one(c, "SELECT password_hash FROM users WHERE id = ?",
                rs -> rs.getString(1), client.id())).orElseThrow();
        assertThat(hash).startsWith("$2a$").doesNotContain(TestBank.CLIENT_PASSWORD);
    }

    @Test
    void clientSignsInWithEmailInAnyCase() {
        LoginResult result = auth.login("  MEHMIL@test.bank ", TestBank.CLIENT_PASSWORD, Role.CLIENT, null);
        assertThat(result).isInstanceOfSatisfying(LoginResult.Success.class,
                s -> assertThat(s.user().id()).isEqualTo(client.id()));
        assertThat(lastOutcome()).isEqualTo(LoginEvent.Outcome.SUCCESS);
    }

    @Test
    void wrongPasswordGivesGenericMessageAndCountsDown() {
        LoginResult result = auth.login("mehmil@test.bank", "nope", Role.CLIENT, null);
        assertThat(result).isInstanceOfSatisfying(LoginResult.Failure.class, f -> {
            assertThat(f.message()).isEqualTo("Incorrect email or password");
            assertThat(f.attemptsLeft()).isEqualTo(AuthService.MAX_FAILED_ATTEMPTS - 1);
        });
    }

    @Test
    void unknownEmailGetsTheSameMessage() {
        LoginResult result = auth.login("ghost@test.bank", "whatever1", Role.CLIENT, null);
        assertThat(result).isInstanceOfSatisfying(LoginResult.Failure.class,
                f -> assertThat(f.message()).isEqualTo("Incorrect email or password"));
        assertThat(lastOutcome()).isEqualTo(LoginEvent.Outcome.UNKNOWN_USER);
    }

    @Test
    void locksAfterRepeatedFailuresThenUnlocksAfterTheLockPeriod() {
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS - 1; i++) {
            assertThat(auth.login("mehmil@test.bank", "wrong" + i, Role.CLIENT, null))
                    .isInstanceOf(LoginResult.Failure.class);
        }
        assertThat(auth.login("mehmil@test.bank", "wrong-last", Role.CLIENT, null))
                .isInstanceOf(LoginResult.Locked.class);

        // Even the right password is refused while locked.
        assertThat(auth.login("mehmil@test.bank", TestBank.CLIENT_PASSWORD, Role.CLIENT, null))
                .isInstanceOf(LoginResult.Locked.class);
        assertThat(lastOutcome()).isEqualTo(LoginEvent.Outcome.LOCKED);

        bank.advance(AuthService.LOCK_DURATION.plus(Duration.ofSeconds(1)));
        assertThat(auth.login("mehmil@test.bank", TestBank.CLIENT_PASSWORD, Role.CLIENT, null))
                .isInstanceOf(LoginResult.Success.class);
    }

    @Test
    void successResetsTheFailureCounter() {
        auth.login("mehmil@test.bank", "wrong1", Role.CLIENT, null);
        auth.login("mehmil@test.bank", TestBank.CLIENT_PASSWORD, Role.CLIENT, null);
        LoginResult result = auth.login("mehmil@test.bank", "wrong2", Role.CLIENT, null);
        assertThat(((LoginResult.Failure) result).attemptsLeft()).isEqualTo(AuthService.MAX_FAILED_ATTEMPTS - 1);
    }

    @Test
    void adminNeedsTheCorrectPin() {
        assertThat(auth.login("admin@test.bank", TestBank.ADMIN_PASSWORD, Role.ADMIN, "0000"))
                .isInstanceOf(LoginResult.Failure.class);
        assertThat(auth.login("admin@test.bank", TestBank.ADMIN_PASSWORD, Role.ADMIN, TestBank.ADMIN_PIN))
                .isInstanceOf(LoginResult.Success.class);
    }

    @Test
    void clientCannotUseTheAdminSignIn() {
        assertThat(auth.login("mehmil@test.bank", TestBank.CLIENT_PASSWORD, Role.ADMIN, "1111"))
                .isInstanceOf(LoginResult.Failure.class);
        assertThat(auth.login("admin@test.bank", TestBank.ADMIN_PASSWORD, Role.CLIENT, null))
                .isInstanceOf(LoginResult.Failure.class);
    }

    @Test
    void changePasswordRequiresTheCurrentOneAndAStrongNewOne() {
        assertThatThrownBy(() -> auth.changePassword(client.id(), "wrong", "newpass123"))
                .hasMessageContaining("Current password is incorrect");
        assertThatThrownBy(() -> auth.changePassword(client.id(), TestBank.CLIENT_PASSWORD, "short1"))
                .hasMessageContaining("at least 8");
        assertThatThrownBy(() -> auth.changePassword(client.id(), TestBank.CLIENT_PASSWORD, "onlyletters"))
                .hasMessageContaining("letters and numbers");

        auth.changePassword(client.id(), TestBank.CLIENT_PASSWORD, "newpass123");
        assertThat(auth.login("mehmil@test.bank", "newpass123", Role.CLIENT, null))
                .isInstanceOf(LoginResult.Success.class);
        assertThat(auth.login("mehmil@test.bank", TestBank.CLIENT_PASSWORD, Role.CLIENT, null))
                .isInstanceOf(LoginResult.Failure.class);
    }

    @Test
    void adminResetIssuesAWorkingTemporaryPasswordAndClearsTheLock() {
        for (int i = 0; i < AuthService.MAX_FAILED_ATTEMPTS; i++) {
            auth.login("mehmil@test.bank", "wrong" + i, Role.CLIENT, null);
        }
        String temporary = auth.resetClientPassword(bank.admin, client.id());

        assertThat(temporary).hasSizeGreaterThanOrEqualTo(Validation.MIN_PASSWORD_LENGTH);
        assertThat(auth.login("mehmil@test.bank", temporary, Role.CLIENT, null)).isInstanceOf(LoginResult.Success.class);
        assertThat(bank.ctx.admin().auditLog(bank.admin, 10))
                .anySatisfy(e -> assertThat(e.action()).isEqualTo("PASSWORD_RESET"));
    }

    @Test
    void temporaryPasswordsPassThePasswordRules() {
        for (int i = 0; i < 200; i++) {
            Validation.password(auth.temporaryPassword());
        }
    }

    @Test
    void onlyAdminsCanResetPasswords() {
        assertThatThrownBy(() -> auth.resetClientPassword(client, client.id()))
                .isInstanceOf(BankException.class)
                .hasMessageContaining("Only admins");
    }

    @Test
    void adminCanChangePin() {
        auth.changePin(bank.admin.id(), TestBank.ADMIN_PASSWORD, "2468");
        assertThat(auth.login("admin@test.bank", TestBank.ADMIN_PASSWORD, Role.ADMIN, "2468"))
                .isInstanceOf(LoginResult.Success.class);
        assertThatThrownBy(() -> auth.changePin(bank.admin.id(), TestBank.ADMIN_PASSWORD, "12"))
                .hasMessageContaining("4 to 6 digits");
    }

    private LoginEvent.Outcome lastOutcome() {
        return bank.ctx.admin().loginActivity(bank.admin, 1).getFirst().outcome();
    }
}
