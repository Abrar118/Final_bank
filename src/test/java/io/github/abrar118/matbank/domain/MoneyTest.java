package io.github.abrar118.matbank.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "100        | 10000",
            "1,250.5    | 125050",
            "  42.07    | 4207",
            "৳ 300      | 30000",
            "BDT 0.01   | 1",
            "1.50       | 150"})
    void parsesUserInput(String input, long cents) {
        assertThat(Money.parse(input)).isEqualTo(Money.ofCents(cents));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "abc", "1.234", "12..5", "1e999999"})
    void rejectsInvalidInput(String input) {
        assertThatThrownBy(() -> Money.parse(input)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void arithmeticIsExact() {
        // 0.1 + 0.2 != 0.3 with doubles, which is how the 2022 balances drifted.
        Money sum = Money.parse("0.10").plus(Money.parse("0.20"));
        assertThat(sum).isEqualTo(Money.parse("0.30"));
    }

    @Test
    void percentagesRoundHalfEvenToThePoisha() {
        assertThat(Money.parse("100.50").times(new BigDecimal("0.05"))).isEqualTo(Money.parse("5.02"));
        assertThat(Money.parse("100.70").times(new BigDecimal("0.05"))).isEqualTo(Money.parse("5.04"));
    }

    @Test
    void formats() {
        assertThat(Money.parse("1234567.5").format()).isEqualTo("BDT 1,234,567.50");
        assertThat(Money.parse("12").negate().format()).isEqualTo("-BDT 12.00");
        assertThat(Money.parse("12").formatSigned()).isEqualTo("+BDT 12.00");
        assertThat(Money.ZERO.plain()).isEqualTo("0.00");
    }
}
