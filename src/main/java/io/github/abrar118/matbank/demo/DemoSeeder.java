package io.github.abrar118.matbank.demo;

import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.db.ActivityRepository;
import io.github.abrar118.matbank.db.UserRepository;
import io.github.abrar118.matbank.domain.AccountType;
import io.github.abrar118.matbank.domain.Frequency;
import io.github.abrar118.matbank.domain.Gender;
import io.github.abrar118.matbank.domain.LoginEvent;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.Role;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.AdminService;
import io.github.abrar118.matbank.service.BankException;
import io.github.abrar118.matbank.service.RecurringPaymentService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Fills an empty database with a small, believable bank so a first-time visitor can sign in straight away
 * and see something. Replays about five months of salaries, transfers and scheduled payments through the real
 * services on a {@link MutableClock}, so every balance and ledger line is consistent.
 */
public final class DemoSeeder {

    public static final String ADMIN_EMAIL = "admin@matbank.demo";
    public static final String ADMIN_PASSWORD = "admin2022";
    /** Same PIN as the 2022 super admin. */
    public static final String ADMIN_PIN = "1111";
    public static final String CLIENT_PASSWORD = "demo1234";

    /** A sign-in shortcut offered on the login screen. */
    public record DemoLogin(String label, String email, String password, Role role, String pin) {
    }

    public static final List<DemoLogin> LOGINS = List.of(
            new DemoLogin("Rahim (client)", "rahim@matbank.demo", CLIENT_PASSWORD, Role.CLIENT, null),
            new DemoLogin("Nusrat (client)", "nusrat@matbank.demo", CLIENT_PASSWORD, Role.CLIENT, null),
            new DemoLogin("Admin", ADMIN_EMAIL, ADMIN_PASSWORD, Role.ADMIN, ADMIN_PIN));

    static final int HISTORY_DAYS = 150;

    private record Event(LocalTime time, Runnable action) {
    }

    private record Persona(String name, String email, Gender gender, LocalDate birth, String phone, String address,
                           long openingChecking, long openingSavings, long salary, String employer) {
    }

    private static final List<Persona> CLIENTS = List.of(
            new Persona("Rahim Uddin", "rahim@matbank.demo", Gender.MALE, LocalDate.of(1996, 4, 12),
                    "+880 1711-000101", "House 12, Road 5, Dhanmondi, Dhaka", 15_000, 60_000, 65_000,
                    "Salary - Padma Software Ltd"),
            new Persona("Nusrat Jahan", "nusrat@matbank.demo", Gender.FEMALE, LocalDate.of(1998, 9, 3),
                    "+880 1811-000202", "Mirpur DOHS, Dhaka", 8_000, 25_000, 48_000,
                    "Salary - Meghna Textiles"),
            new Persona("Tanvir Ahmed", "tanvir@matbank.demo", Gender.MALE, LocalDate.of(1994, 1, 20),
                    "+880 1911-000303", "Agrabad, Chattogram", 20_000, 40_000, 72_000,
                    "Salary - Karnaphuli Shipping"),
            new Persona("Farhana Akter", "farhana@matbank.demo", Gender.FEMALE, LocalDate.of(2000, 11, 30),
                    "+880 1611-000404", "Zindabazar, Sylhet", 5_000, 12_000, 35_000,
                    "Stipend - MIST Research Lab"),
            new Persona("Sabbir Hossain", "sabbir@matbank.demo", Gender.MALE, LocalDate.of(1990, 6, 15),
                    "+880 1511-000505", "Shaheb Bazar, Rajshahi", 30_000, 150_000, 90_000,
                    "Salary - Jamuna Pharma"));

    private static final String[] NOTES = {
            "Lunch at Star Kabab", "Cricket match tickets", "Dinner split", "Eid gift", "Books for the semester",
            "Movie night", "Group project supplies", "Bus fare back from Cox's Bazar", "Birthday gift",
            "Pizza split", "Electricity bill share", "Concert tickets", "Fuchka treat", "Gym membership share"};

    private static final String[] DEPOSIT_REFERENCES = {
            "Cash deposit - Gulshan branch", "Freelance - logo design", "Tuition refund", "Prize money - hackathon",
            "Sold old laptop", "Cash deposit - Agrabad branch"};

    private final AppContext context;

    public DemoSeeder(AppContext context) {
        this.context = context;
    }

    public static boolean isEmpty(AppContext context) {
        return context.database().read(c -> new UserRepository().countAll(c)) == 0;
    }

    /** Seeds demo data unless the database already has users. Slow (password hashing), so run it off the FX thread. */
    public boolean seedIfEmpty() {
        if (!isEmpty(context)) {
            return false;
        }
        seed();
        return true;
    }

    void seed() {
        ZoneId zone = context.clock().getZone();
        Instant realNow = context.clock().instant();
        LocalDate today = LocalDate.ofInstant(realNow, zone);
        LocalDate start = today.minusDays(HISTORY_DAYS);
        MutableClock clock = new MutableClock(start.atTime(9, 0).atZone(zone).toInstant(), zone);
        AppContext sim = new AppContext(context.database(), clock, context.hasher(), context.exchangeRates());
        Random random = new Random(2022);
        UserRepository users = new UserRepository();
        ActivityRepository activity = new ActivityRepository();

        User admin = createAdmin(users, clock.instant());
        List<User> clients = new ArrayList<>();
        for (Persona p : CLIENTS) {
            clients.add(sim.admin().createClient(admin, new AdminService.NewClient(p.name(), p.email(),
                    CLIENT_PASSWORD, p.gender(), p.birth(), p.phone(), "facebook.com/" + p.email().split("@")[0] + ".demo",
                    p.address(), Money.of(p.openingChecking()), Money.of(p.openingSavings()))));
        }
        User rahim = clients.get(0);
        User nusrat = clients.get(1);
        User tanvir = clients.get(2);
        User farhana = clients.get(3);
        User sabbir = clients.get(4);

        at(clock, start, 10, 0);
        sim.recurring().create(rahim.id(), new RecurringPaymentService.NewPayment(
                AccountType.CHECKING, sabbir.email(), Money.of(18_000), "Rent - Flat 4B, Dhanmondi",
                Frequency.MONTHLY, start.plusDays(4)));
        sim.recurring().create(nusrat.id(), new RecurringPaymentService.NewPayment(
                AccountType.SAVINGS, farhana.email(), Money.of(2_500), "Study group fees",
                Frequency.MONTHLY, start.plusDays(10)));
        long cricketDues = sim.recurring().create(rahim.id(), new RecurringPaymentService.NewPayment(
                AccountType.CHECKING, tanvir.email(), Money.of(500), "Cricket club dues",
                Frequency.WEEKLY, start.plusDays(20))).id();

        for (LocalDate day = start.plusDays(1); day.isBefore(today); day = day.plusDays(1)) {
            // Collect the day's events, then replay them in time order so ledger timestamps only move forward.
            List<Event> events = new ArrayList<>();
            events.add(new Event(LocalTime.of(6, 0), () -> sim.recurring().runDuePayments()));

            if (day.getDayOfMonth() == 1) {
                for (int i = 0; i < clients.size(); i++) {
                    User client = clients.get(i);
                    Persona p = CLIENTS.get(i);
                    events.add(new Event(LocalTime.of(9, 15 + i * 7), () -> sim.banking().deposit(client.id(),
                            AccountType.CHECKING, Money.of(p.salary()), p.employer())));
                }
                events.add(new Event(LocalTime.of(11, 40), () ->
                        sim.banking().moveBetweenAccounts(rahim.id(), AccountType.CHECKING, Money.of(10_000))));
                events.add(new Event(LocalTime.of(12, 5), () ->
                        sim.banking().moveBetweenAccounts(tanvir.id(), AccountType.CHECKING, Money.of(15_000))));
            }

            int transfers = random.nextInt(100) < 55 ? 1 + random.nextInt(2) : 0;
            for (int t = 0; t < transfers; t++) {
                User from = clients.get(random.nextInt(clients.size()));
                User to = clients.get(random.nextInt(clients.size()));
                long amount = 150 + random.nextInt(60) * 50L;
                String note = NOTES[random.nextInt(NOTES.length)];
                LocalTime time = LocalTime.of(13 + random.nextInt(8), random.nextInt(60));
                if (from.id() != to.id()) {
                    events.add(new Event(time, () -> sim.banking().transfer(from.id(), AccountType.CHECKING,
                            to.email(), Money.of(amount), note)));
                }
            }

            if (random.nextInt(100) < 12) {
                User who = clients.get(random.nextInt(clients.size()));
                long amount = 1_000 + random.nextInt(40) * 250L;
                String ref = DEPOSIT_REFERENCES[random.nextInt(DEPOSIT_REFERENCES.length)];
                AccountType account = random.nextBoolean() ? AccountType.CHECKING : AccountType.SAVINGS;
                events.add(new Event(LocalTime.of(16, random.nextInt(60)), () ->
                        sim.banking().deposit(who.id(), account, Money.of(amount), ref)));
            }

            if (day.equals(start.plusDays(80))) {
                events.add(new Event(LocalTime.of(18, 0), () -> sim.recurring().pause(rahim.id(), cricketDues)));
            }

            events.sort(Comparator.comparing(Event::time));
            for (Event event : events) {
                clock.set(day.atTime(event.time()).atZone(zone).toInstant());
                tryRun(event.action());
            }

            for (User u : clients) {
                if (random.nextInt(100) < 30) {
                    Instant at = day.atTime(LocalTime.of(8 + random.nextInt(13), random.nextInt(60))).atZone(zone).toInstant();
                    context.database().write(c -> activity.recordLogin(c, u.id(), u.email(), u.fullName(),
                            LoginEvent.Outcome.SUCCESS, at));
                }
            }
        }

        seedSupport(sim, clock, today, admin, nusrat, tanvir, rahim, farhana, sabbir);

        LocalDate yesterday = today.minusDays(1);
        Instant failed = yesterday.atTime(22, 41).atZone(zone).toInstant();
        context.database().write(c -> {
            activity.recordLogin(c, tanvir.id(), tanvir.email(), null, LoginEvent.Outcome.WRONG_CREDENTIALS, failed);
            activity.recordLogin(c, tanvir.id(), tanvir.email(), tanvir.fullName(), LoginEvent.Outcome.SUCCESS,
                    failed.plusSeconds(40));
            activity.recordLogin(c, null, "someone@example.com", null, LoginEvent.Outcome.UNKNOWN_USER,
                    failed.minusSeconds(3_600));
        });
    }

    private User createAdmin(UserRepository users, Instant now) {
        String passwordHash = context.hasher().hash(ADMIN_PASSWORD);
        String pinHash = context.hasher().hash(ADMIN_PIN);
        return context.database().inTransaction(c -> {
            long id = users.insert(c, new UserRepository.NewUser(ADMIN_EMAIL, passwordHash, pinHash, Role.ADMIN,
                    "Abrar Mahir Esam", Gender.MALE, LocalDate.of(2001, 9, 11), "+880 1700-000000",
                    "github.com/Abrar118", "Mirpur DOHS, Dhaka"), now);
            new ActivityRepository().audit(c, id, ADMIN_EMAIL, "ADMIN_CREATED", "Demo super admin created", now);
            return users.findById(c, id).orElseThrow();
        });
    }

    private static void seedSupport(AppContext sim, MutableClock clock, LocalDate today, User admin, User nusrat,
                                    User tanvir, User rahim, User farhana, User sabbir) {
        // Tanvir mistypes his password until the account locks, an admin issues a temporary password, and he
        // sets his usual one again. All through the real services, so the sign-in history and audit log agree.
        for (int i = 0; i < 5; i++) {
            at(clock, today.minusDays(9), 19, 30 + i);
            sim.auth().login(tanvir.email(), "tanvir" + i, Role.CLIENT, null);
        }
        at(clock, today.minusDays(9), 19, 52);
        String temporary = sim.auth().resetClientPassword(admin, tanvir.id());
        at(clock, today.minusDays(9), 20, 3);
        sim.auth().login(tanvir.email(), temporary, Role.CLIENT, null);
        sim.auth().changePassword(tanvir.id(), temporary, CLIENT_PASSWORD);

        at(clock, today.minusDays(9), 20, 12);
        sim.support().sendMessage(tanvir, null, null,
                "I got locked out after mistyping my password a few times. Thanks for the quick reset!");
        at(clock, today.minusDays(3), 10, 30);
        sim.support().sendMessage(null, "Rafi Chowdhury", "rafi@example.com",
                "Do you offer student accounts? I study at MIST and would like to open one.");
        at(clock, today.minusDays(1), 19, 5);
        sim.support().sendMessage(nusrat, null, null,
                "Hello! Could you raise my transfer limit? I'm moving flats next month and the advance is large.");

        at(clock, today.minusDays(40), 21, 0);
        sim.support().submitFeedback(rahim, 5, "The new dashboard is so much clearer than before. Love the dark mode!");
        at(clock, today.minusDays(22), 9, 45);
        sim.support().submitFeedback(farhana, 4, "PDF statements are super handy. Would love a mobile app too.");
        at(clock, today.minusDays(6), 13, 20);
        sim.support().submitFeedback(sabbir, 5, "Scheduled payments mean I never miss collecting rent.");
    }

    private static void at(MutableClock clock, LocalDate day, int hour, int minute) {
        clock.set(day.atTime(hour, minute).atZone(clock.getZone()).toInstant());
    }

    /** Demo transfers can bounce for lack of funds, just like real ones. */
    private static void tryRun(Runnable action) {
        try {
            action.run();
        } catch (BankException ignored) {
            // Skip it; the demo history doesn't need every attempt to succeed.
        }
    }
}
