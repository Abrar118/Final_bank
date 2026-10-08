package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.TestBank;
import io.github.abrar118.matbank.domain.LedgerEntry;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.TxKind;
import io.github.abrar118.matbank.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static io.github.abrar118.matbank.domain.AccountType.CHECKING;
import static io.github.abrar118.matbank.domain.AccountType.SAVINGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BankingServiceTest {

    @TempDir
    Path dir;

    TestBank bank;
    BankingService banking;
    User trisha;
    User abrar;

    @BeforeEach
    void setUp() {
        bank = TestBank.create(dir);
        banking = bank.ctx.banking();
        trisha = bank.client("Farheen Mahjarin Trisha", "trisha@test.bank", 1_000, 5_000);
        abrar = bank.client("Abrar Mahir Esam", "abrar@test.bank", 1_000, 5_000);
    }

    @Test
    void newClientsGetBothAccountsWithOpeningBalances() {
        assertThat(banking.accounts(trisha.id())).extracting(a -> a.type()).containsExactly(CHECKING, SAVINGS);
        assertThat(bank.balance(trisha, CHECKING)).isEqualTo(Money.of(1_000));
        assertThat(bank.balance(trisha, SAVINGS)).isEqualTo(Money.of(5_000));
        assertThat(banking.accounts(trisha.id()).getFirst().number()).matches("2022 \\d{4} \\d{4}");
        bank.assertLedgerConsistent();
    }

    @Test
    void depositCreditsTheAmountLessTheTwoPercentFee() {
        Receipt receipt = banking.deposit(trisha.id(), SAVINGS, Money.of(500), "Cash at Mirpur branch");

        assertThat(receipt.fee()).isEqualTo(Money.of(10));
        assertThat(receipt.total()).isEqualTo(Money.of(490));
        assertThat(bank.balance(trisha, SAVINGS)).isEqualTo(Money.of(5_490));
        assertThat(entries(trisha, receipt.reference())).extracting(LedgerEntry::kind)
                .containsExactlyInAnyOrder(TxKind.DEPOSIT, TxKind.FEE);
        bank.assertLedgerConsistent();
    }

    @Test
    void transferChargesTheSenderTwoPercentNetAndCreditsTheRecipientTheFullAmount() {
        Receipt receipt = banking.transfer(trisha.id(), CHECKING, "ABRAR@test.bank", Money.of(200), "Lunch");

        assertThat(receipt.fee()).isEqualTo(Money.of(4)); // 5% charge (10) less 3% discount (6)
        assertThat(receipt.total()).isEqualTo(Money.of(204));
        assertThat(receipt.counterparty()).isEqualTo("Abrar Mahir Esam");
        assertThat(bank.balance(trisha, CHECKING)).isEqualTo(Money.of(796));
        assertThat(bank.balance(abrar, CHECKING)).isEqualTo(Money.of(1_200));

        assertThat(entries(trisha, receipt.reference())).extracting(LedgerEntry::kind)
                .containsExactlyInAnyOrder(TxKind.TRANSFER_OUT, TxKind.FEE);
        assertThat(entries(abrar, receipt.reference())).singleElement().satisfies(e -> {
            assertThat(e.kind()).isEqualTo(TxKind.TRANSFER_IN);
            assertThat(e.counterparty()).isEqualTo("trisha@test.bank");
            assertThat(e.note()).isEqualTo("Lunch");
            assertThat(e.description()).isEqualTo("From Farheen Mahjarin Trisha");
        });
        bank.assertLedgerConsistent();
    }

    @Test
    void transferToAnUnknownRecipientChangesNothing() {
        // In 2022 the sender was debited before the recipient lookup, so this lost money.
        assertThatThrownBy(() -> banking.transfer(trisha.id(), CHECKING, "nobody@test.bank", Money.of(100), null))
                .isInstanceOfSatisfying(BankException.class,
                        e -> assertThat(e.reason()).isEqualTo(BankException.Reason.RECIPIENT_NOT_FOUND));
        assertThat(bank.balance(trisha, CHECKING)).isEqualTo(Money.of(1_000));
        assertThat(bank.ctx.ledger().recent(trisha.id(), 10)).hasSize(2); // only the opening balances
    }

    @Test
    void insufficientFundsChangesNothing() {
        assertThatThrownBy(() -> banking.transfer(trisha.id(), CHECKING, "abrar@test.bank", Money.of(990), null))
                .isInstanceOfSatisfying(BankException.class, e -> {
                    assertThat(e.reason()).isEqualTo(BankException.Reason.INSUFFICIENT_FUNDS);
                    assertThat(e.getMessage()).contains("BDT 1,009.80");
                });
        assertThat(bank.balance(trisha, CHECKING)).isEqualTo(Money.of(1_000));
        assertThat(bank.balance(abrar, CHECKING)).isEqualTo(Money.of(1_000));
        bank.assertLedgerConsistent();
    }

    @Test
    void cannotTransferToYourself() {
        assertThatThrownBy(() -> banking.transfer(trisha.id(), CHECKING, "trisha@test.bank", Money.of(10), null))
                .hasMessageContaining("Between my accounts");
    }

    @Test
    void cannotTransferToAnAdmin() {
        assertThatThrownBy(() -> banking.transfer(trisha.id(), CHECKING, "admin@test.bank", Money.of(10), null))
                .isInstanceOfSatisfying(BankException.class,
                        e -> assertThat(e.reason()).isEqualTo(BankException.Reason.RECIPIENT_NOT_FOUND));
    }

    @Test
    void enforcesAmountLimits() {
        assertThatThrownBy(() -> banking.deposit(trisha.id(), CHECKING, Money.parse("0.50"), "x"))
                .hasMessageContaining("minimum");
        assertThatThrownBy(() -> banking.deposit(trisha.id(), CHECKING, Money.of(1_000_001), "x"))
                .hasMessageContaining("maximum");
        assertThatThrownBy(() -> banking.deposit(trisha.id(), CHECKING, Money.of(10), " "))
                .hasMessageContaining("Reference");
    }

    @Test
    void movingBetweenOwnAccountsIsFree() {
        Receipt receipt = banking.moveBetweenAccounts(trisha.id(), SAVINGS, Money.of(2_500));

        assertThat(receipt.fee()).isEqualTo(Money.ZERO);
        assertThat(bank.balance(trisha, SAVINGS)).isEqualTo(Money.of(2_500));
        assertThat(bank.balance(trisha, CHECKING)).isEqualTo(Money.of(3_500));
        assertThatThrownBy(() -> banking.moveBetweenAccounts(trisha.id(), SAVINGS, Money.of(2_501)))
                .isInstanceOf(BankException.class);
        bank.assertLedgerConsistent();
    }

    @Test
    void concurrentTransfersNeverOverdraw() throws Exception {
        // 20 transfers of 100 (+2 fee each) race for 1,000: at most 9 can succeed.
        var pool = Executors.newFixedThreadPool(8);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            tasks.add(() -> {
                try {
                    banking.transfer(trisha.id(), CHECKING, "abrar@test.bank", Money.of(100), null);
                    return true;
                } catch (BankException e) {
                    return false;
                }
            });
        }
        long succeeded = pool.invokeAll(tasks).stream().filter(f -> {
            try {
                return f.get();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }).count();
        pool.shutdown();

        assertThat(succeeded).isEqualTo(9);
        assertThat(bank.balance(trisha, CHECKING)).isEqualTo(Money.of(1_000 - 9 * 102));
        assertThat(bank.balance(abrar, CHECKING)).isEqualTo(Money.of(1_000 + 9 * 100));
        bank.assertLedgerConsistent();
    }

    @Test
    void findsRecipientsByEmailButNotAdmins() {
        assertThat(banking.findRecipient("Abrar@Test.Bank")).map(User::fullName).hasValue("Abrar Mahir Esam");
        assertThat(banking.findRecipient("admin@test.bank")).isEmpty();
        assertThat(banking.findRecipient("")).isEmpty();
    }

    private List<LedgerEntry> entries(User user, String reference) {
        return bank.ctx.ledger().search(user.id(), LedgerService.HistoryFilter.text(reference));
    }
}
