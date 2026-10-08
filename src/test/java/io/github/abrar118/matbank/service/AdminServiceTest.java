package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.TestBank;
import io.github.abrar118.matbank.domain.Gender;
import io.github.abrar118.matbank.domain.LedgerEntry;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.AdminService.NewClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;

import static io.github.abrar118.matbank.domain.AccountType.CHECKING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminServiceTest {

    @TempDir
    Path dir;

    TestBank bank;
    AdminService admin;

    @BeforeEach
    void setUp() {
        bank = TestBank.create(dir);
        admin = bank.ctx.admin();
    }

    @Test
    void createsClientsAndAuditsIt() {
        User created = admin.createClient(bank.admin, newClient("New.Client@Test.Bank"));

        assertThat(created.email()).isEqualTo("new.client@test.bank");
        assertThat(admin.clients(bank.admin, "")).extracting(User::email).containsExactly("new.client@test.bank");
        assertThat(admin.auditLog(bank.admin, 5)).first().satisfies(e -> {
            assertThat(e.action()).isEqualTo("CLIENT_CREATED");
            assertThat(e.actorEmail()).isEqualTo("admin@test.bank");
        });
    }

    @Test
    void rejectsDuplicatesAndBadInput() {
        admin.createClient(bank.admin, newClient("dup@test.bank"));
        assertThatThrownBy(() -> admin.createClient(bank.admin, newClient("DUP@test.bank")))
                .hasMessageContaining("already exists");
        assertThatThrownBy(() -> admin.createClient(bank.admin, newClient("not-an-email")))
                .hasMessageContaining("valid email");
        var tooYoung = new NewClient("Kid", "kid@test.bank", "secret123", Gender.MALE, LocalDate.of(2020, 1, 1),
                null, null, null, Money.ZERO, Money.ZERO);
        assertThatThrownBy(() -> admin.createClient(bank.admin, tooYoung)).hasMessageContaining("13 years");
    }

    @Test
    void searchMatchesNameOrEmail() {
        bank.client("Mehmil Khan", "mehmil@test.bank", 0, 0);
        bank.client("Farheen Trisha", "trisha@test.bank", 0, 0);
        assertThat(admin.clients(bank.admin, "khan")).extracting(User::fullName).containsExactly("Mehmil Khan");
        assertThat(admin.clients(bank.admin, "TRISHA@")).hasSize(1);
        assertThat(admin.clients(bank.admin, "_")).isEmpty();
    }

    @Test
    void deletingAClientKeepsTheOtherSideOfTheirTransfers() {
        User leaving = bank.client("Leaving Soon", "leaving@test.bank", 1_000, 0);
        User staying = bank.client("Staying Put", "staying@test.bank", 0, 0);
        bank.ctx.banking().transfer(leaving.id(), CHECKING, staying.email(), Money.of(100), "Goodbye gift");

        admin.deleteClient(bank.admin, leaving.id());

        assertThat(admin.clients(bank.admin, null)).extracting(User::email).containsExactly("staying@test.bank");
        assertThat(bank.ctx.ledger().recent(staying.id(), 5)).extracting(LedgerEntry::counterparty)
                .contains("leaving@test.bank");
        assertThat(admin.auditLog(bank.admin, 1).getFirst().details()).contains("BDT 898.00");
        bank.assertLedgerConsistent();
    }

    @Test
    void clientsCannotUseAdminTools() {
        User client = bank.client("Just A Client", "client@test.bank", 0, 0);
        assertThatThrownBy(() -> admin.clients(client, "")).hasMessageContaining("Only admins");
        assertThatThrownBy(() -> admin.deleteClient(client, client.id())).hasMessageContaining("Only admins");
        assertThatThrownBy(() -> admin.overview(client, 7)).hasMessageContaining("Only admins");
    }

    @Test
    void overviewSummarisesTheBank() {
        User a = bank.client("Client A", "a@test.bank", 1_000, 2_000);
        User b = bank.client("Client B", "b@test.bank", 500, 0);
        bank.ctx.banking().transfer(a.id(), CHECKING, b.email(), Money.of(100), null);
        bank.ctx.banking().deposit(b.id(), CHECKING, Money.of(300), "Cash");
        bank.ctx.auth().login("a@test.bank", "wrong", io.github.abrar118.matbank.domain.Role.CLIENT, null);
        bank.ctx.support().sendMessage(null, "Visitor", null, "Hello");

        var overview = admin.overview(bank.admin, 7);

        assertThat(overview.clients()).isEqualTo(2);
        assertThat(overview.holdings()).isEqualTo(Money.of(3_500 - 2 + 300 - 6));
        assertThat(overview.failedSignInsToday()).isEqualTo(1);
        assertThat(overview.unreadMessages()).isEqualTo(1);
        assertThat(overview.volume()).hasSize(7);
        assertThat(overview.volume().getLast().transfers()).isEqualTo(Money.of(100));
        assertThat(overview.volume().getLast().deposits()).isEqualTo(Money.of(300));
    }

    private static NewClient newClient(String email) {
        return new NewClient("Test Client", email, "secret123", Gender.OTHER, LocalDate.of(1990, 1, 1), null, null,
                null, AdminService.DEFAULT_OPENING_CHECKING, AdminService.DEFAULT_OPENING_SAVINGS);
    }
}
