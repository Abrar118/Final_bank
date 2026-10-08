package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.domain.AccountType;
import io.github.abrar118.matbank.domain.Money;

import java.time.Instant;

/**
 * Confirmation of a completed operation.
 *
 * @param amount       what the customer asked to move or deposit
 * @param fee          charge applied (zero when free)
 * @param total        what left the account (transfers) or what was credited (deposits)
 * @param newBalance   balance of {@code account} afterwards
 * @param counterparty display name of the other side, or {@code null}
 */
public record Receipt(String reference, Instant at, AccountType account, Money amount, Money fee, Money total,
                      Money newBalance, String counterparty) {
}
