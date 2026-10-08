package io.github.abrar118.matbank;

import io.github.abrar118.matbank.db.AccountRepository;
import io.github.abrar118.matbank.db.ActivityRepository;
import io.github.abrar118.matbank.db.Database;
import io.github.abrar118.matbank.db.LedgerRepository;
import io.github.abrar118.matbank.db.Migrator;
import io.github.abrar118.matbank.db.RecurringPaymentRepository;
import io.github.abrar118.matbank.db.SupportRepository;
import io.github.abrar118.matbank.db.UserRepository;
import io.github.abrar118.matbank.service.AdminService;
import io.github.abrar118.matbank.service.AuthService;
import io.github.abrar118.matbank.service.BankingService;
import io.github.abrar118.matbank.service.ExchangeRateService;
import io.github.abrar118.matbank.service.FeePolicy;
import io.github.abrar118.matbank.service.Identifiers;
import io.github.abrar118.matbank.service.LedgerService;
import io.github.abrar118.matbank.service.PasswordHasher;
import io.github.abrar118.matbank.service.ProfileService;
import io.github.abrar118.matbank.service.RecurringPaymentService;
import io.github.abrar118.matbank.service.StatementService;
import io.github.abrar118.matbank.service.SupportService;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Wires the database, repositories and services together. Replaces the 2022 {@code Model} singleton:
 * screens receive this context explicitly instead of reaching for global state.
 */
public final class AppContext implements AutoCloseable {

    private final Clock clock;
    private final Database database;
    private final PasswordHasher hasher;
    private final AuthService auth;
    private final BankingService banking;
    private final LedgerService ledger;
    private final ProfileService profiles;
    private final SupportService support;
    private final AdminService admin;
    private final RecurringPaymentService recurring;
    private final StatementService statements;
    private final ExchangeRateService exchangeRates;
    private ScheduledExecutorService scheduler;

    public AppContext(Database database, Clock clock, PasswordHasher hasher, ExchangeRateService exchangeRates) {
        this.clock = clock;
        this.database = database;
        this.hasher = hasher;
        this.exchangeRates = exchangeRates;

        var users = new UserRepository();
        var accounts = new AccountRepository();
        var entries = new LedgerRepository();
        var activity = new ActivityRepository();
        var supportRepo = new SupportRepository();
        var recurringRepo = new RecurringPaymentRepository();

        this.auth = new AuthService(database, users, activity, hasher, clock);
        this.banking = new BankingService(database, users, accounts, entries, new FeePolicy(), new Identifiers(), clock);
        this.ledger = new LedgerService(database, accounts, entries, clock);
        this.profiles = new ProfileService(database, users, activity, clock);
        this.support = new SupportService(database, supportRepo, clock);
        this.admin = new AdminService(database, users, accounts, entries, activity, supportRepo, banking, hasher, clock);
        this.recurring = new RecurringPaymentService(database, recurringRepo, users, banking, clock);
        this.statements = new StatementService(database, users, accounts, entries, clock);
    }

    /** Opens (and migrates) the database in {@code paths} with production settings. */
    public static AppContext open(AppPaths paths, Clock clock) {
        paths.create();
        Database database = new Database(paths.database());
        new Migrator(database, clock).migrate();
        var rates = new ExchangeRateService(ExchangeRateService.httpFetcher(), paths.exchangeRateCache(), clock);
        return new AppContext(database, clock, new PasswordHasher(PasswordHasher.DEFAULT_COST), rates);
    }

    /**
     * Processes due scheduled payments now and then every minute on a background thread.
     *
     * @param onRun called (on the scheduler thread) after a run that paid or skipped anything
     */
    public synchronized void startScheduler(Consumer<RecurringPaymentService.RunSummary> onRun) {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("matbank-scheduler").factory());
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                var summary = recurring.runDuePayments();
                if (!summary.isEmpty()) {
                    onRun.accept(summary);
                }
            } catch (RuntimeException e) {
                System.getLogger("matbank").log(System.Logger.Level.WARNING, "Scheduled payments run failed", e);
            }
        }, 0, 1, TimeUnit.MINUTES);
    }

    @Override
    public synchronized void close() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public Clock clock() {
        return clock;
    }

    public Database database() {
        return database;
    }

    public PasswordHasher hasher() {
        return hasher;
    }

    public AuthService auth() {
        return auth;
    }

    public BankingService banking() {
        return banking;
    }

    public LedgerService ledger() {
        return ledger;
    }

    public ProfileService profiles() {
        return profiles;
    }

    public SupportService support() {
        return support;
    }

    public AdminService admin() {
        return admin;
    }

    public RecurringPaymentService recurring() {
        return recurring;
    }

    public StatementService statements() {
        return statements;
    }

    public ExchangeRateService exchangeRates() {
        return exchangeRates;
    }
}
