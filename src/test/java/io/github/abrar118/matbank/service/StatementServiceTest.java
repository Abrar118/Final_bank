package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.TestBank;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.User;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;

import static io.github.abrar118.matbank.domain.AccountType.CHECKING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatementServiceTest {

    @TempDir
    Path dir;

    TestBank bank;
    User mehmil;
    User trisha;

    @BeforeEach
    void setUp() {
        bank = TestBank.create(dir);
        mehmil = bank.client("Mehmil Khan", "mehmil@test.bank", 5_000, 0);   // 15 Mar
        trisha = bank.client("Farheen Trisha", "trisha@test.bank", 0, 0);
        bank.advance(Duration.ofDays(20));                                     // 4 Apr
        bank.ctx.banking().transfer(mehmil.id(), CHECKING, trisha.email(), Money.of(1_000), "Concert tickets");
        bank.advance(Duration.ofDays(2));                                      // 6 Apr
        bank.ctx.banking().deposit(mehmil.id(), CHECKING, Money.of(250), "Cash");
    }

    @Test
    void computesOpeningAndClosingBalancesForThePeriod() {
        var s = bank.ctx.statements().build(mehmil.id(), CHECKING, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));

        assertThat(s.opening()).isEqualTo(Money.of(5_000));
        assertThat(s.entries()).hasSize(4); // transfer, fee, deposit, deposit fee
        assertThat(s.moneyOut()).isEqualTo(Money.of(1_025));
        assertThat(s.moneyIn()).isEqualTo(Money.of(250));
        assertThat(s.closing()).isEqualTo(Money.of(4_225));
        assertThat(s.to()).isEqualTo(LocalDate.of(2026, 4, 6)); // capped at today
    }

    @Test
    void emptyPeriodCarriesTheBalanceForward() {
        var s = bank.ctx.statements().build(mehmil.id(), CHECKING, LocalDate.of(2026, 3, 20), LocalDate.of(2026, 3, 31));
        assertThat(s.entries()).isEmpty();
        assertThat(s.opening()).isEqualTo(Money.of(5_000));
        assertThat(s.closing()).isEqualTo(Money.of(5_000));
    }

    @Test
    void rendersAReadablePdf() throws Exception {
        var s = bank.ctx.statements().build(mehmil.id(), CHECKING, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30));
        var out = new ByteArrayOutputStream();
        bank.ctx.statements().writePdf(s, out);

        try (PDDocument pdf = Loader.loadPDF(out.toByteArray())) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Mehmil Khan", "Checking account", "Concert tickets", "To Farheen Trisha",
                    "Opening balance", "Closing balance", "4,225.00", "Page 1 of 1");
            assertThat(pdf.getDocumentInformation().getTitle()).startsWith("MAT Bank statement");
        }
    }

    @Test
    void longStatementsSpanSeveralPages() throws Exception {
        for (int i = 0; i < 60; i++) {
            bank.advance(Duration.ofMinutes(5));
            bank.ctx.banking().deposit(mehmil.id(), CHECKING, Money.of(10), "Coins jar #" + i);
        }
        var s = bank.ctx.statements().build(mehmil.id(), CHECKING, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 30));
        var out = new ByteArrayOutputStream();
        bank.ctx.statements().writePdf(s, out);

        try (PDDocument pdf = Loader.loadPDF(out.toByteArray())) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Coins jar #59", "Page " + pdf.getNumberOfPages() + " of " + pdf.getNumberOfPages());
        }
    }

    @Test
    void nonLatinTextDoesNotBreakTheStandardFonts() throws Exception {
        bank.ctx.banking().deposit(mehmil.id(), CHECKING, Money.of(100), "ঈদ উপহার");
        var s = bank.ctx.statements().build(mehmil.id(), CHECKING, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30));
        var out = new ByteArrayOutputStream();
        bank.ctx.statements().writePdf(s, out);
        assertThat(out.size()).isPositive();
    }

    @Test
    void validatesTheDateRange() {
        assertThatThrownBy(() -> bank.ctx.statements().build(mehmil.id(), CHECKING, LocalDate.of(2026, 4, 2),
                LocalDate.of(2026, 4, 1))).isInstanceOf(BankException.class);
    }
}
