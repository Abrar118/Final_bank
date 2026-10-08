package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.TestBank;
import io.github.abrar118.matbank.domain.LedgerEntry;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.TxKind;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.LedgerService.HistoryFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;

import static io.github.abrar118.matbank.domain.AccountType.CHECKING;
import static io.github.abrar118.matbank.domain.AccountType.SAVINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerServiceTest {

    @TempDir
    Path dir;

    TestBank bank;
    LedgerService ledger;
    User rahim;
    User nusrat;

    @BeforeEach
    void setUp() {
        bank = TestBank.create(dir);
        ledger = bank.ctx.ledger();
        rahim = bank.client("Rahim Uddin", "rahim@test.bank", 10_000, 20_000);   // 15 Mar
        nusrat = bank.client("Nusrat Jahan", "nusrat@test.bank", 1_000, 1_000);
        bank.advance(Duration.ofDays(1));                                          // 16 Mar
        bank.ctx.banking().transfer(rahim.id(), CHECKING, nusrat.email(), Money.of(500), "Cricket tickets");
        bank.advance(Duration.ofDays(20));                                         // 5 Apr
        bank.ctx.banking().deposit(rahim.id(), SAVINGS, Money.of(1_000), "Freelance - logo design");
        bank.ctx.banking().moveBetweenAccounts(rahim.id(), SAVINGS, Money.of(300));
    }

    @Test
    void recentIsNewestFirst() {
        assertThat(ledger.recent(rahim.id(), 3)).extracting(LedgerEntry::kind)
                .containsExactly(TxKind.INTERNAL_IN, TxKind.INTERNAL_OUT, TxKind.FEE);
    }

    @Test
    void searchesNotesDescriptionsAndCounterparties() {
        assertThat(ledger.search(rahim.id(), HistoryFilter.text("cricket")))
                .singleElement().extracting(LedgerEntry::kind).isEqualTo(TxKind.TRANSFER_OUT);
        assertThat(ledger.search(rahim.id(), HistoryFilter.text("nusrat@")))
                .singleElement().extracting(LedgerEntry::kind).isEqualTo(TxKind.TRANSFER_OUT);
        assertThat(ledger.search(rahim.id(), HistoryFilter.text("logo"))).hasSize(1);
        assertThat(ledger.search(rahim.id(), HistoryFilter.text("100%"))).isEmpty(); // LIKE wildcards are escaped
    }

    @Test
    void filtersByAccountCategoryAndDate() {
        assertThat(ledger.search(rahim.id(), new HistoryFilter(SAVINGS, null, null, null, null)))
                .allMatch(e -> e.accountType() == SAVINGS);
        assertThat(ledger.search(rahim.id(), new HistoryFilter(null, TxKind.Category.FEE, null, null, null)))
                .hasSize(2).allMatch(e -> e.kind() == TxKind.FEE);
        assertThat(ledger.search(rahim.id(), new HistoryFilter(null, null, null,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30)))).hasSize(4);
        assertThat(ledger.search(rahim.id(), new HistoryFilter(null, null, null,
                LocalDate.of(2026, 3, 16), LocalDate.of(2026, 3, 16)))).hasSize(2);
    }

    @Test
    void rejectsBackwardsDateRange() {
        assertThatThrownBy(() -> ledger.search(rahim.id(), new HistoryFilter(null, null, null,
                LocalDate.of(2026, 4, 2), LocalDate.of(2026, 4, 1)))).isInstanceOf(BankException.class);
    }

    @Test
    void monthlyTotalsIgnoreMovesBetweenOwnAccounts() {
        var totals = ledger.monthlyTotals(rahim.id(), 3);
        assertThat(totals).extracting(LedgerService.MonthTotals::month)
                .containsExactly(YearMonth.of(2026, 2), YearMonth.of(2026, 3), YearMonth.of(2026, 4));
        var march = totals.get(1);
        assertThat(march.in()).isEqualTo(Money.of(30_000));   // opening balances
        assertThat(march.out()).isEqualTo(Money.of(510));     // 500 + 10 fee
        var april = totals.get(2);
        assertThat(april.in()).isEqualTo(Money.of(1_000));
        assertThat(april.out()).isEqualTo(Money.of(20));      // deposit fee only
    }

    @Test
    void balanceTrendEndsAtTheCurrentTotal() {
        var trend = ledger.balanceTrend(rahim.id(), 30);
        assertThat(trend).hasSize(30);
        assertThat(trend.getLast().date()).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(trend.getLast().total()).isEqualTo(Money.of(30_000 - 510 + 980));
        assertThat(trend.getFirst().total()).isEqualTo(Money.ZERO); // before the accounts were opened
    }
}
