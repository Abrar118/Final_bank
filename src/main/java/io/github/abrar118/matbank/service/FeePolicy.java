package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.domain.Money;

import java.math.BigDecimal;

/**
 * The charges from the 2022 app, kept as a tribute but now applied consistently.
 *
 * <ul>
 *   <li>Transfers to other clients: 5% service charge with a 3% discount, so 2% net, paid by the sender
 *       on top of the amount. (In 2022 the recipient's balance was updated even when the recipient didn't exist.)</li>
 *   <li>Deposits: 2% handling fee, deducted from the deposited amount.</li>
 *   <li>Moving money between your own accounts is free.</li>
 * </ul>
 */
public final class FeePolicy {

    public static final BigDecimal TRANSFER_CHARGE_RATE = new BigDecimal("0.05");
    public static final BigDecimal TRANSFER_DISCOUNT_RATE = new BigDecimal("0.03");
    public static final BigDecimal DEPOSIT_FEE_RATE = new BigDecimal("0.02");

    public static final Money MIN_AMOUNT = Money.of(1);
    public static final Money MAX_AMOUNT = Money.of(1_000_000);

    public record TransferQuote(Money amount, Money charge, Money discount, Money fee, Money total) {
    }

    public record DepositQuote(Money amount, Money fee, Money credited) {
    }

    public TransferQuote transfer(Money amount) {
        Money charge = amount.times(TRANSFER_CHARGE_RATE);
        Money discount = amount.times(TRANSFER_DISCOUNT_RATE);
        Money fee = charge.minus(discount);
        return new TransferQuote(amount, charge, discount, fee, amount.plus(fee));
    }

    public DepositQuote deposit(Money amount) {
        Money fee = amount.times(DEPOSIT_FEE_RATE);
        return new DepositQuote(amount, fee, amount.minus(fee));
    }

    public void checkAmount(Money amount) {
        if (amount == null || amount.isLessThan(MIN_AMOUNT)) {
            throw BankException.validation("The minimum amount is " + MIN_AMOUNT.format());
        }
        if (amount.isGreaterThan(MAX_AMOUNT)) {
            throw BankException.validation("The maximum for a single transaction is " + MAX_AMOUNT.format());
        }
    }
}
