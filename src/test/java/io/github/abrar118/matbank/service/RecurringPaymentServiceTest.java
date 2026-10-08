package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.TestBank;
import io.github.abrar118.matbank.domain.Frequency;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.RecurringPayment;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.RecurringPaymentService.NewPayment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;

import static io.github.abrar118.matbank.domain.AccountType.CHECKING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecurringPaymentServiceTest {

    @TempDir
    Path dir;

    TestBank bank;
    RecurringPaymentService recurring;
    User tenant;
    User landlord;
    LocalDate today;

    @BeforeEach
    void setUp() {
        bank = TestBank.create(dir);
        recurring = bank.ctx.recurring();
        tenant = bank.client("Tanvir Ahmed", "tanvir@test.bank", 10_000, 0);
        landlord = bank.client("Sabbir Hossain", "sabbir@test.bank", 0, 0);
        today = LocalDate.now(bank.clock); // 2026-03-15
    }

    @Test
    void paysWhenDueAndSchedulesTheNextRun() {
        RecurringPayment p = recurring.create(tenant.id(), new NewPayment(CHECKING, landlord.email(), Money.of(1_000),
                "Rent", Frequency.MONTHLY, today));

        var summary = recurring.runDuePayments();

        assertThat(summary.paid()).isEqualTo(1);
        assertThat(bank.balance(landlord, CHECKING)).isEqualTo(Money.of(1_000));
        assertThat(bank.balance(tenant, CHECKING)).isEqualTo(Money.of(10_000 - 1_020));
        RecurringPayment after = recurring.list(tenant.id()).getFirst();
        assertThat(after.nextRun()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(after.lastResult()).startsWith("Paid BDT 1,020.00");

        // Running again the same day pays nothing more.
        assertThat(recurring.runDuePayments().isEmpty()).isTrue();
        assertThat(p.id()).isEqualTo(after.id());
        bank.assertLedgerConsistent();
    }

    @Test
    void catchesUpOccurrencesMissedWhileTheAppWasClosed() {
        recurring.create(tenant.id(), new NewPayment(CHECKING, landlord.email(), Money.of(100), null,
                Frequency.WEEKLY, today));
        bank.advance(Duration.ofDays(21)); // occurrences on day 0, 7, 14 and 21

        assertThat(recurring.runDuePayments().paid()).isEqualTo(4);
        assertThat(bank.balance(landlord, CHECKING)).isEqualTo(Money.of(400));
        assertThat(recurring.list(tenant.id()).getFirst().nextRun()).isEqualTo(today.plusWeeks(4));
    }

    @Test
    void skipsAnOccurrenceWhenFundsAreShortAndTriesAgainNextTime() {
        recurring.create(tenant.id(), new NewPayment(CHECKING, landlord.email(), Money.of(5_000), null,
                Frequency.DAILY, today));
        bank.advance(Duration.ofDays(1));

        var summary = recurring.runDuePayments();

        assertThat(summary.paid()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        RecurringPayment p = recurring.list(tenant.id()).getFirst();
        assertThat(p.active()).isTrue();
        assertThat(p.lastResult()).startsWith("Skipped").contains("Not enough money");
        assertThat(p.nextRun()).isEqualTo(today.plusDays(2));
    }

    @Test
    void stopsWhenTheRecipientClosesTheirAccount() {
        recurring.create(tenant.id(), new NewPayment(CHECKING, landlord.email(), Money.of(100), null,
                Frequency.WEEKLY, today.plusDays(1)));
        bank.ctx.admin().deleteClient(bank.admin, landlord.id());
        bank.advance(Duration.ofDays(1));

        assertThat(recurring.runDuePayments().skipped()).isEqualTo(1);
        RecurringPayment p = recurring.list(tenant.id()).getFirst();
        assertThat(p.active()).isFalse();
        assertThat(p.lastResult()).startsWith("Stopped");
    }

    @Test
    void monthlyPaymentsKeepTheirDayOfMonth() {
        bank.clock.set(LocalDate.of(2026, 1, 31).atTime(9, 0).atZone(TestBank.DHAKA).toInstant());
        recurring.create(tenant.id(), new NewPayment(CHECKING, landlord.email(), Money.of(10), null,
                Frequency.MONTHLY, LocalDate.of(2026, 1, 31)));
        bank.clock.set(LocalDate.of(2026, 3, 31).atTime(9, 0).atZone(TestBank.DHAKA).toInstant());

        assertThat(recurring.runDuePayments().paid()).isEqualTo(3); // 31 Jan, 28 Feb, 31 Mar
        assertThat(recurring.list(tenant.id()).getFirst().nextRun()).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void pausedPaymentsDoNotRunAndResumeWithoutBackPay() {
        RecurringPayment p = recurring.create(tenant.id(), new NewPayment(CHECKING, landlord.email(), Money.of(100),
                null, Frequency.WEEKLY, today));
        recurring.pause(tenant.id(), p.id());
        bank.advance(Duration.ofDays(15));
        assertThat(recurring.runDuePayments().isEmpty()).isTrue();

        recurring.resume(tenant.id(), p.id());
        RecurringPayment resumed = recurring.list(tenant.id()).getFirst();
        assertThat(resumed.nextRun()).isEqualTo(today.plusWeeks(3));
        assertThat(recurring.runDuePayments().isEmpty()).isTrue();
        assertThat(bank.balance(landlord, CHECKING)).isEqualTo(Money.ZERO);
    }

    @Test
    void validatesNewPayments() {
        assertThatThrownBy(() -> recurring.create(tenant.id(), new NewPayment(CHECKING, "ghost@test.bank",
                Money.of(10), null, Frequency.WEEKLY, today))).hasMessageContaining("No MAT Bank client");
        assertThatThrownBy(() -> recurring.create(tenant.id(), new NewPayment(CHECKING, tenant.email(),
                Money.of(10), null, Frequency.WEEKLY, today))).hasMessageContaining("yourself");
        assertThatThrownBy(() -> recurring.create(tenant.id(), new NewPayment(CHECKING, landlord.email(),
                Money.of(10), null, Frequency.WEEKLY, today.minusDays(1)))).hasMessageContaining("past");
    }

    @Test
    void clientsCannotTouchOtherClientsPayments() {
        RecurringPayment p = recurring.create(tenant.id(), new NewPayment(CHECKING, landlord.email(), Money.of(10),
                null, Frequency.WEEKLY, today.plusDays(1)));
        assertThatThrownBy(() -> recurring.delete(landlord.id(), p.id())).isInstanceOf(BankException.class);
        recurring.delete(tenant.id(), p.id());
        assertThat(recurring.list(tenant.id())).isEmpty();
    }
}
