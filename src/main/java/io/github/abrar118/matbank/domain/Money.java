package io.github.abrar118.matbank.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * An amount of Bangladeshi Taka, held as whole poisha (cents) so arithmetic is exact.
 * The 2022 version used {@code double}, which drifted by fractions of a taka on every fee.
 */
public record Money(long cents) implements Comparable<Money> {

    public static final String CURRENCY = "BDT";
    public static final Money ZERO = new Money(0);

    private static final ThreadLocal<DecimalFormat> FORMAT = ThreadLocal.withInitial(() ->
            new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)));

    public static Money ofCents(long cents) {
        return new Money(cents);
    }

    public static Money of(long wholeTaka) {
        return new Money(Math.multiplyExact(wholeTaka, 100));
    }

    public static Money of(BigDecimal amount) {
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_EVEN);
        return new Money(scaled.movePointRight(2).longValueExact());
    }

    /**
     * Parses user input such as {@code "1,250.5"} or {@code "৳ 300"}.
     *
     * @throws IllegalArgumentException if the text is not a number with at most two decimals
     */
    public static Money parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Enter an amount");
        }
        String cleaned = text.strip()
                .replace(",", "")
                .replace("৳", "")
                .replace(CURRENCY, "")
                .strip();
        BigDecimal value;
        try {
            value = new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + text.strip() + "\" is not a valid amount");
        }
        if (value.precision() - value.scale() > 15) {
            throw new IllegalArgumentException("Amount is too large");
        }
        if (value.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amounts can have at most two decimal places");
        }
        try {
            return of(value);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Amount is too large");
        }
    }

    public BigDecimal toBigDecimal() {
        return BigDecimal.valueOf(cents, 2);
    }

    public Money plus(Money other) {
        return new Money(Math.addExact(cents, other.cents));
    }

    public Money minus(Money other) {
        return new Money(Math.subtractExact(cents, other.cents));
    }

    public Money negate() {
        return new Money(Math.negateExact(cents));
    }

    public Money abs() {
        return cents < 0 ? negate() : this;
    }

    /** {@code this × rate}, rounded half-even to the nearest poisha. */
    public Money times(BigDecimal rate) {
        return of(toBigDecimal().multiply(rate));
    }

    public boolean isPositive() {
        return cents > 0;
    }

    public boolean isNegative() {
        return cents < 0;
    }

    public boolean isZero() {
        return cents == 0;
    }

    public boolean isLessThan(Money other) {
        return cents < other.cents;
    }

    public boolean isGreaterThan(Money other) {
        return cents > other.cents;
    }

    @Override
    public int compareTo(Money other) {
        return Long.compare(cents, other.cents);
    }

    /** {@code "1,234.50"} without a currency code. */
    public String plain() {
        return FORMAT.get().format(toBigDecimal());
    }

    /** {@code "BDT 1,234.50"}, or {@code "-BDT 1,234.50"} for negative amounts. */
    public String format() {
        return (cents < 0 ? "-" : "") + CURRENCY + " " + abs().plain();
    }

    /** Like {@link #format()} but always shows the sign, for ledger lines. */
    public String formatSigned() {
        return (cents < 0 ? "-" : "+") + CURRENCY + " " + abs().plain();
    }

    @Override
    public String toString() {
        return format();
    }
}
