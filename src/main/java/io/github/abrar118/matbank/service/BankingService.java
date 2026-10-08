package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.db.AccountRepository;
import io.github.abrar118.matbank.db.Database;
import io.github.abrar118.matbank.db.LedgerRepository;
import io.github.abrar118.matbank.db.UserRepository;
import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.AccountType;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.Role;
import io.github.abrar118.matbank.domain.TxKind;
import io.github.abrar118.matbank.domain.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Deposits and transfers. Every operation runs in one database transaction: either every balance and
 * ledger line changes, or none do.
 */
public final class BankingService {

    public static final int MAX_NOTE_LENGTH = 140;

    private final Database db;
    private final UserRepository users;
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final FeePolicy fees;
    private final Identifiers ids;
    private final Clock clock;

    public BankingService(Database db, UserRepository users, AccountRepository accounts, LedgerRepository ledger,
                          FeePolicy fees, Identifiers ids, Clock clock) {
        this.db = db;
        this.users = users;
        this.accounts = accounts;
        this.ledger = ledger;
        this.fees = fees;
        this.ids = ids;
        this.clock = clock;
    }

    public FeePolicy fees() {
        return fees;
    }

    public List<Account> accounts(long userId) {
        return db.read(c -> accounts.findByUser(c, userId));
    }

    public Account account(long userId, AccountType type) {
        return db.read(c -> accounts.find(c, userId, type)).orElseThrow(() ->
                new BankException(BankException.Reason.ACCOUNT_NOT_FOUND, "No " + type.label() + " account"));
    }

    /** Looks up a client by email so the sender can confirm who they're paying. */
    public Optional<User> findRecipient(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return db.read(c -> users.findByEmail(c, email)).filter(u -> u.role() == Role.CLIENT);
    }

    /** Creates both accounts for a new client, crediting any opening balances. */
    void openAccounts(Connection c, long userId, Money checking, Money savings, Instant now) throws SQLException {
        for (AccountType type : AccountType.values()) {
            String number;
            do {
                number = ids.accountNumber();
            } while (accounts.numberExists(c, number));
            long accountId = accounts.insert(c, userId, type, number, now);
            Money opening = type == AccountType.CHECKING ? checking : savings;
            if (opening.isPositive()) {
                Money balance = accounts.credit(c, accountId, opening);
                ledger.insert(c, accountId, TxKind.OPENING_BALANCE, opening, balance, null,
                        "Opening balance", null, ids.reference(), now);
            }
        }
    }

    public Receipt deposit(long userId, AccountType to, Money amount, String reference) {
        fees.checkAmount(amount);
        String ref = Validation.maxLength(Validation.required(reference, "Reference"), 60, "Reference");
        FeePolicy.DepositQuote quote = fees.deposit(amount);
        Instant now = clock.instant();
        String txRef = ids.reference();
        return db.inTransaction(c -> {
            Account account = requireAccount(c, userId, to);
            Money afterDeposit = accounts.credit(c, account.id(), amount);
            ledger.insert(c, account.id(), TxKind.DEPOSIT, amount, afterDeposit, null,
                    "Deposit to " + to.label(), ref, txRef, now);
            Money afterFee = afterDeposit;
            if (quote.fee().isPositive()) {
                afterFee = accounts.debit(c, account.id(), quote.fee()).orElseThrow();
                ledger.insert(c, account.id(), TxKind.FEE, quote.fee().negate(), afterFee, null,
                        "Deposit fee (2%)", null, txRef, now);
            }
            return new Receipt(txRef, now, to, amount, quote.fee(), quote.credited(), afterFee, null);
        });
    }

    public Receipt transfer(long senderId, AccountType from, String recipientEmail, Money amount, String note) {
        return db.inTransaction(c -> transfer(c, senderId, from, recipientEmail, amount, note, clock.instant()));
    }

    /** Transfer inside a caller-managed transaction; used by scheduled payments to pay and reschedule atomically. */
    Receipt transfer(Connection c, long senderId, AccountType from, String recipientEmail, Money amount, String note,
                     Instant now) throws SQLException {
        fees.checkAmount(amount);
        String email = Validation.email(recipientEmail);
        String message = Validation.maxLength(Validation.optional(note), MAX_NOTE_LENGTH, "Message");

        User sender = users.findById(c, senderId).filter(u -> u.role() == Role.CLIENT)
                .orElseThrow(() -> new BankException(BankException.Reason.NOT_ALLOWED, "Only clients can send money"));
        if (sender.email().equalsIgnoreCase(email)) {
            throw new BankException(BankException.Reason.NOT_ALLOWED,
                    "That's your own email. Use \"Between my accounts\" to move your own money.");
        }
        User recipient = users.findByEmail(c, email).filter(u -> u.role() == Role.CLIENT)
                .orElseThrow(() -> new BankException(BankException.Reason.RECIPIENT_NOT_FOUND,
                        "No MAT Bank client uses " + email));

        Account source = requireAccount(c, senderId, from);
        Account destination = requireAccount(c, recipient.id(), AccountType.CHECKING);
        FeePolicy.TransferQuote quote = fees.transfer(amount);

        Money afterFee = accounts.debit(c, source.id(), quote.total()).orElseThrow(() ->
                new BankException(BankException.Reason.INSUFFICIENT_FUNDS, "Not enough money in " + from.label()
                        + ". This transfer needs " + quote.total().format() + " including the "
                        + quote.fee().format() + " service charge."));
        String ref = ids.reference();
        ledger.insert(c, source.id(), TxKind.TRANSFER_OUT, amount.negate(), afterFee.plus(quote.fee()),
                recipient.email(), "To " + recipient.fullName(), message, ref, now);
        if (quote.fee().isPositive()) {
            ledger.insert(c, source.id(), TxKind.FEE, quote.fee().negate(), afterFee, null,
                    "Transfer charge (2% net)", null, ref, now);
        }
        Money recipientBalance = accounts.credit(c, destination.id(), amount);
        ledger.insert(c, destination.id(), TxKind.TRANSFER_IN, amount, recipientBalance,
                sender.email(), "From " + sender.fullName(), message, ref, now);

        return new Receipt(ref, now, from, amount, quote.fee(), quote.total(), afterFee, recipient.fullName());
    }

    /** Moves money between a client's own checking and savings accounts. Free of charge. */
    public Receipt moveBetweenAccounts(long userId, AccountType from, Money amount) {
        fees.checkAmount(amount);
        AccountType to = from.other();
        Instant now = clock.instant();
        String ref = ids.reference();
        return db.inTransaction(c -> {
            Account source = requireAccount(c, userId, from);
            Account destination = requireAccount(c, userId, to);
            Money sourceBalance = accounts.debit(c, source.id(), amount).orElseThrow(() ->
                    new BankException(BankException.Reason.INSUFFICIENT_FUNDS,
                            "Not enough money in " + from.label() + " to move " + amount.format()));
            ledger.insert(c, source.id(), TxKind.INTERNAL_OUT, amount.negate(), sourceBalance, null,
                    "Moved to " + to.label(), null, ref, now);
            Money destinationBalance = accounts.credit(c, destination.id(), amount);
            ledger.insert(c, destination.id(), TxKind.INTERNAL_IN, amount, destinationBalance, null,
                    "Moved from " + from.label(), null, ref, now);
            return new Receipt(ref, now, from, amount, Money.ZERO, amount, sourceBalance, to.label());
        });
    }

    private Account requireAccount(Connection c, long userId, AccountType type) throws SQLException {
        return accounts.find(c, userId, type).orElseThrow(() ->
                new BankException(BankException.Reason.ACCOUNT_NOT_FOUND, "No " + type.label() + " account"));
    }
}
