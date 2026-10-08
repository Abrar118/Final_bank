package io.github.abrar118.matbank.domain;

import java.time.Instant;

/**
 * One line on an account's statement.
 *
 * @param amount       signed: positive for credits, negative for debits
 * @param balanceAfter the account balance immediately after this line
 * @param counterparty the other party's email, or {@code null} for deposits and charges
 * @param description  what happened, written by the bank (e.g. "To Nusrat Jahan")
 * @param note         the customer's own message or deposit reference, if any
 * @param reference    shared by every line that belongs to the same operation (e.g. a transfer and its fee)
 */
public record LedgerEntry(
        long id,
        long accountId,
        AccountType accountType,
        TxKind kind,
        Money amount,
        Money balanceAfter,
        String counterparty,
        String description,
        String note,
        String reference,
        Instant createdAt) {

    public boolean isCredit() {
        return amount.isPositive();
    }
}
