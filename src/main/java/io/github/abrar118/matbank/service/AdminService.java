package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.db.AccountRepository;
import io.github.abrar118.matbank.db.ActivityRepository;
import io.github.abrar118.matbank.db.Database;
import io.github.abrar118.matbank.db.LedgerRepository;
import io.github.abrar118.matbank.db.SupportRepository;
import io.github.abrar118.matbank.db.UserRepository;
import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.AuditEvent;
import io.github.abrar118.matbank.domain.Gender;
import io.github.abrar118.matbank.domain.LedgerEntry;
import io.github.abrar118.matbank.domain.LoginEvent;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.Role;
import io.github.abrar118.matbank.domain.TxKind;
import io.github.abrar118.matbank.domain.User;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Admin-only operations: managing clients and reviewing activity. Every method checks the caller is an admin. */
public final class AdminService {

    public static final Money DEFAULT_OPENING_CHECKING = Money.of(1_000);
    public static final Money DEFAULT_OPENING_SAVINGS = Money.of(5_000);

    public record NewClient(String fullName, String email, String password, Gender gender, LocalDate birthDate,
                            String phone, String facebook, String address, Money openingChecking,
                            Money openingSavings) {
    }

    public record ClientDetails(User client, List<Account> accounts, List<LedgerEntry> recent) {

        public Money totalBalance() {
            return accounts.stream().map(Account::balance).reduce(Money.ZERO, Money::plus);
        }
    }

    /** Money moved by clients on one day: deposits and transfers sent, as totals and as counts. */
    public record DailyVolume(LocalDate date, Money deposits, Money transfers, int depositCount, int transferCount) {
    }

    public record Overview(long clients, Money holdings, long signInsToday, long failedSignInsToday,
                           long unreadMessages, List<DailyVolume> volume) {
    }

    private final Database db;
    private final UserRepository users;
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final ActivityRepository activity;
    private final SupportRepository support;
    private final BankingService banking;
    private final PasswordHasher hasher;
    private final Clock clock;

    public AdminService(Database db, UserRepository users, AccountRepository accounts, LedgerRepository ledger,
                        ActivityRepository activity, SupportRepository support, BankingService banking,
                        PasswordHasher hasher, Clock clock) {
        this.db = db;
        this.users = users;
        this.accounts = accounts;
        this.ledger = ledger;
        this.activity = activity;
        this.support = support;
        this.banking = banking;
        this.hasher = hasher;
        this.clock = clock;
    }

    public List<User> clients(User admin, String search) {
        AuthService.requireAdmin(admin);
        return db.read(c -> users.findClients(c, search));
    }

    public ClientDetails clientDetails(User admin, long clientId) {
        AuthService.requireAdmin(admin);
        return db.read(c -> {
            User client = users.findById(c, clientId).filter(u -> u.role() == Role.CLIENT)
                    .orElseThrow(() -> new BankException(BankException.Reason.ACCOUNT_NOT_FOUND, "Client not found"));
            return new ClientDetails(client, accounts.findByUser(c, clientId), ledger.recent(c, clientId, 200));
        });
    }

    /** Opens a client profile with a checking and a savings account. Hashes the password, so call it off the FX thread. */
    public User createClient(User admin, NewClient request) {
        AuthService.requireAdmin(admin);
        String name = Validation.maxLength(Validation.required(request.fullName(), "Full name"), 80, "Full name");
        String email = Validation.email(request.email());
        Validation.password(request.password());
        if (request.gender() == null) {
            throw BankException.validation("Select a gender");
        }
        ProfileService.checkBirthDate(request.birthDate(), clock);
        Money checking = request.openingChecking() == null ? Money.ZERO : request.openingChecking();
        Money savings = request.openingSavings() == null ? Money.ZERO : request.openingSavings();
        if (checking.isNegative() || savings.isNegative()) {
            throw BankException.validation("Opening balances can't be negative");
        }
        if (checking.isGreaterThan(FeePolicy.MAX_AMOUNT) || savings.isGreaterThan(FeePolicy.MAX_AMOUNT)) {
            throw BankException.validation("Opening balances are limited to " + FeePolicy.MAX_AMOUNT.format());
        }
        if (db.read(c -> users.emailExists(c, email))) {
            throw new BankException(BankException.Reason.DUPLICATE, "An account with " + email + " already exists");
        }
        String hash = hasher.hash(request.password());
        Instant now = clock.instant();
        return db.inTransaction(c -> {
            long id = users.insert(c, new UserRepository.NewUser(email, hash, null, Role.CLIENT, name,
                    request.gender(), request.birthDate(),
                    Validation.optional(request.phone()), Validation.optional(request.facebook()),
                    Validation.optional(request.address())), now);
            banking.openAccounts(c, id, checking, savings, now);
            activity.audit(c, admin.id(), admin.email(), "CLIENT_CREATED",
                    "Opened accounts for " + email + " (checking " + checking.format() + ", savings "
                            + savings.format() + ")", now);
            return users.findById(c, id).orElseThrow();
        });
    }

    /** Closes a client's profile and accounts. Past transfers stay on other clients' statements. */
    public void deleteClient(User admin, long clientId) {
        AuthService.requireAdmin(admin);
        db.write(c -> {
            User client = users.findById(c, clientId).filter(u -> u.role() == Role.CLIENT)
                    .orElseThrow(() -> new BankException(BankException.Reason.ACCOUNT_NOT_FOUND, "Client not found"));
            Money total = accounts.findByUser(c, clientId).stream().map(Account::balance)
                    .reduce(Money.ZERO, Money::plus);
            users.delete(c, clientId);
            activity.audit(c, admin.id(), admin.email(), "CLIENT_DELETED",
                    "Closed " + client.email() + " (" + client.fullName() + "), balance at closing "
                            + total.format(), clock.instant());
        });
    }

    public List<LoginEvent> loginActivity(User admin, int limit) {
        AuthService.requireAdmin(admin);
        return db.read(c -> activity.logins(c, limit));
    }

    public List<AuditEvent> auditLog(User admin, int limit) {
        AuthService.requireAdmin(admin);
        return db.read(c -> activity.auditLog(c, limit));
    }

    public Overview overview(User admin, int days) {
        AuthService.requireAdmin(admin);
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        Instant startOfToday = today.atStartOfDay(zone).toInstant();
        LocalDate first = today.minusDays(days - 1L);
        return db.read(c -> {
            Map<LocalDate, long[]> buckets = new LinkedHashMap<>();
            for (LocalDate d = first; !d.isAfter(today); d = d.plusDays(1)) {
                buckets.put(d, new long[4]);
            }
            for (LedgerEntry e : ledger.allSince(c, first.atStartOfDay(zone).toInstant())) {
                long[] bucket = buckets.get(LocalDate.ofInstant(e.createdAt(), zone));
                if (bucket == null) {
                    continue;
                }
                if (e.kind() == TxKind.DEPOSIT) {
                    bucket[0] += e.amount().cents();
                    bucket[2]++;
                } else if (e.kind() == TxKind.TRANSFER_OUT) {
                    bucket[1] += e.amount().abs().cents();
                    bucket[3]++;
                }
            }
            List<DailyVolume> volume = new ArrayList<>();
            buckets.forEach((d, b) -> volume.add(new DailyVolume(d, Money.ofCents(b[0]), Money.ofCents(b[1]),
                    (int) b[2], (int) b[3])));
            return new Overview(
                    users.count(c, Role.CLIENT),
                    accounts.totalHoldings(c),
                    activity.loginsSince(c, LoginEvent.Outcome.SUCCESS, startOfToday),
                    activity.loginsSince(c, LoginEvent.Outcome.WRONG_CREDENTIALS, startOfToday)
                            + activity.loginsSince(c, LoginEvent.Outcome.UNKNOWN_USER, startOfToday),
                    support.unreadCount(c),
                    volume);
        });
    }
}
