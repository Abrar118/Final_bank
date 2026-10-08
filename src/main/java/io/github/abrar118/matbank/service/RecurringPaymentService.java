package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.db.Database;
import io.github.abrar118.matbank.db.RecurringPaymentRepository;
import io.github.abrar118.matbank.db.UserRepository;
import io.github.abrar118.matbank.domain.AccountType;
import io.github.abrar118.matbank.domain.Frequency;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.RecurringPayment;
import io.github.abrar118.matbank.domain.Role;
import io.github.abrar118.matbank.domain.User;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Standing orders. Due payments are processed when the app starts and then every minute while it runs,
 * so occurrences that fell due while the app was closed are caught up (at most {@link #MAX_CATCH_UP} at a time).
 */
public final class RecurringPaymentService {

    public static final int MAX_CATCH_UP = 31;

    public record NewPayment(AccountType from, String recipientEmail, Money amount, String note,
                             Frequency frequency, LocalDate startDate) {
    }

    /** What a processing run did. */
    public record RunSummary(int paid, int skipped) {

        public boolean isEmpty() {
            return paid == 0 && skipped == 0;
        }
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final Database db;
    private final RecurringPaymentRepository payments;
    private final UserRepository users;
    private final BankingService banking;
    private final Clock clock;

    public RecurringPaymentService(Database db, RecurringPaymentRepository payments, UserRepository users,
                                   BankingService banking, Clock clock) {
        this.db = db;
        this.payments = payments;
        this.users = users;
        this.banking = banking;
        this.clock = clock;
    }

    public List<RecurringPayment> list(long userId) {
        return db.read(c -> payments.forUser(c, userId));
    }

    public RecurringPayment create(long userId, NewPayment request) {
        banking.fees().checkAmount(request.amount());
        String email = Validation.email(request.recipientEmail());
        String note = Validation.maxLength(Validation.optional(request.note()), BankingService.MAX_NOTE_LENGTH, "Note");
        if (request.from() == null || request.frequency() == null) {
            throw BankException.validation("Choose an account and how often to pay");
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate start = request.startDate() == null ? today : request.startDate();
        if (start.isBefore(today)) {
            throw BankException.validation("The first payment can't be in the past");
        }
        return db.inTransaction(c -> {
            User owner = users.findById(c, userId).orElseThrow();
            if (owner.email().equalsIgnoreCase(email)) {
                throw new BankException(BankException.Reason.NOT_ALLOWED, "You can't schedule payments to yourself");
            }
            users.findByEmail(c, email).filter(u -> u.role() == Role.CLIENT).orElseThrow(() ->
                    new BankException(BankException.Reason.RECIPIENT_NOT_FOUND, "No MAT Bank client uses " + email));
            long id = payments.insert(c, userId, request.from(), email, request.amount(), note, request.frequency(),
                    start, clock.instant());
            return payments.find(c, id).orElseThrow();
        });
    }

    public void pause(long userId, long paymentId) {
        RecurringPayment p = owned(userId, paymentId);
        db.write(c -> payments.setActive(c, p.id(), false, null));
    }

    /** Resumes from the next occurrence on or after today; occurrences missed while paused are not paid. */
    public void resume(long userId, long paymentId) {
        RecurringPayment p = owned(userId, paymentId);
        LocalDate today = LocalDate.now(clock);
        int runs = p.runsCompleted();
        while (p.frequency().occurrence(p.startDate(), runs).isBefore(today)) {
            runs++;
        }
        int nextRuns = runs;
        db.write(c -> {
            payments.reschedule(c, p.id(), nextRuns, p.frequency().occurrence(p.startDate(), nextRuns));
            payments.setActive(c, p.id(), true, null);
        });
    }

    public void delete(long userId, long paymentId) {
        RecurringPayment p = owned(userId, paymentId);
        db.write(c -> payments.delete(c, p.id()));
    }

    /** Pays every occurrence that is due today or earlier. Safe to call repeatedly. */
    public RunSummary runDuePayments() {
        LocalDate today = LocalDate.now(clock);
        int paid = 0;
        int skipped = 0;
        for (RecurringPayment due : db.read(c -> payments.due(c, today))) {
            RecurringPayment p = due;
            int processed = 0;
            while (p.active() && !p.nextRun().isAfter(today) && processed < MAX_CATCH_UP) {
                if (runOnce(p)) {
                    paid++;
                } else {
                    skipped++;
                }
                processed++;
                long id = p.id();
                p = db.read(c -> payments.find(c, id)).orElse(null);
                if (p == null) {
                    break;
                }
            }
        }
        return new RunSummary(paid, skipped);
    }

    /** Processes one occurrence: pay it and advance the schedule in the same transaction. */
    private boolean runOnce(RecurringPayment p) {
        LocalDate occurrence = p.nextRun();
        int runs = p.runsCompleted() + 1;
        LocalDate next = p.frequency().occurrence(p.startDate(), runs);
        try {
            db.write(c -> {
                String note = p.note() == null ? "Scheduled payment" : p.note();
                Receipt receipt = banking.transfer(c, p.userId(), p.fromAccount(), p.recipientEmail(), p.amount(),
                        note, clock.instant());
                payments.recordRun(c, p.id(), runs, next,
                        "Paid " + receipt.total().format() + " on " + DATE.format(occurrence));
            });
            return true;
        } catch (BankException e) {
            boolean recipientGone = e.reason() == BankException.Reason.RECIPIENT_NOT_FOUND;
            db.write(c -> {
                payments.recordRun(c, p.id(), runs, next, "Skipped " + DATE.format(occurrence) + ": " + e.getMessage());
                if (recipientGone) {
                    payments.setActive(c, p.id(), false, "Stopped: " + e.getMessage());
                }
            });
            return false;
        }
    }

    private RecurringPayment owned(long userId, long paymentId) {
        return db.read(c -> payments.find(c, paymentId))
                .filter(p -> p.userId() == userId)
                .orElseThrow(() -> new BankException(BankException.Reason.NOT_ALLOWED, "Scheduled payment not found"));
    }
}
