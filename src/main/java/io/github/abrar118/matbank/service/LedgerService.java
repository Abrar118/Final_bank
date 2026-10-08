package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.db.AccountRepository;
import io.github.abrar118.matbank.db.Database;
import io.github.abrar118.matbank.db.LedgerRepository;
import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.AccountType;
import io.github.abrar118.matbank.domain.LedgerEntry;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.TxKind;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads a client's history: recent activity, search and the numbers behind the dashboard charts. */
public final class LedgerService {

    /**
     * Filter for the history screen. Null fields don't filter.
     *
     * @param from inclusive
     * @param to   inclusive
     */
    public record HistoryFilter(AccountType account, TxKind.Category category, String text, LocalDate from,
                                LocalDate to) {

        public static HistoryFilter all() {
            return new HistoryFilter(null, null, null, null, null);
        }

        public static HistoryFilter text(String text) {
            return new HistoryFilter(null, null, text, null, null);
        }
    }

    /** Money in and out of the client's accounts in one month, ignoring moves between their own accounts. */
    public record MonthTotals(YearMonth month, Money in, Money out) {
    }

    public record DailyBalance(LocalDate date, Money total) {
    }

    public static final int MAX_RESULTS = 2000;

    private final Database db;
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final Clock clock;

    public LedgerService(Database db, AccountRepository accounts, LedgerRepository ledger, Clock clock) {
        this.db = db;
        this.accounts = accounts;
        this.ledger = ledger;
        this.clock = clock;
    }

    public ZoneId zone() {
        return clock.getZone();
    }

    public List<LedgerEntry> recent(long userId, int limit) {
        return db.read(c -> ledger.recent(c, userId, limit));
    }

    public List<LedgerEntry> search(long userId, HistoryFilter filter) {
        Instant from = filter.from() == null ? null : filter.from().atStartOfDay(zone()).toInstant();
        Instant to = filter.to() == null ? null : filter.to().plusDays(1).atStartOfDay(zone()).toInstant();
        if (from != null && to != null && !from.isBefore(to)) {
            throw BankException.validation("The start date must be on or before the end date");
        }
        var query = new LedgerRepository.Query(userId, filter.account(), filter.category(), filter.text(), from, to,
                MAX_RESULTS);
        return db.read(c -> ledger.search(c, query));
    }

    /** Totals for the last {@code months} calendar months, oldest first, including the current month. */
    public List<MonthTotals> monthlyTotals(long userId, int months) {
        YearMonth current = YearMonth.now(clock);
        YearMonth first = current.minusMonths(months - 1L);
        Instant since = first.atDay(1).atStartOfDay(zone()).toInstant();
        List<LedgerEntry> entries = db.read(c -> ledger.forUserSince(c, userId, since));

        Map<YearMonth, long[]> totals = new LinkedHashMap<>();
        for (int i = 0; i < months; i++) {
            totals.put(first.plusMonths(i), new long[2]);
        }
        for (LedgerEntry e : entries) {
            if (e.kind().category() == TxKind.Category.INTERNAL) {
                continue;
            }
            long[] bucket = totals.get(YearMonth.from(e.createdAt().atZone(zone())));
            if (bucket != null) {
                bucket[e.isCredit() ? 0 : 1] += e.amount().abs().cents();
            }
        }
        List<MonthTotals> result = new ArrayList<>();
        totals.forEach((month, t) -> result.add(new MonthTotals(month, Money.ofCents(t[0]), Money.ofCents(t[1]))));
        return result;
    }

    /** End-of-day combined balance of all the client's accounts for the last {@code days} days, oldest first. */
    public List<DailyBalance> balanceTrend(long userId, int days) {
        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(days - 1L);
        Instant since = start.atStartOfDay(zone()).toInstant();
        return db.read(c -> {
            long running = 0;
            for (Account account : accounts.findByUser(c, userId)) {
                running += ledger.balanceBefore(c, account.id(), since).cents();
            }
            List<LedgerEntry> entries = ledger.forUserSince(c, userId, since);
            List<DailyBalance> result = new ArrayList<>();
            int index = 0;
            for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
                Instant endOfDay = day.plusDays(1).atStartOfDay(zone()).toInstant();
                while (index < entries.size() && entries.get(index).createdAt().isBefore(endOfDay)) {
                    running += entries.get(index).amount().cents();
                    index++;
                }
                result.add(new DailyBalance(day, Money.ofCents(running)));
            }
            return result;
        });
    }
}
