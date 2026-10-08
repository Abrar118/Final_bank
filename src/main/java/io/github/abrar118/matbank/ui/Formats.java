package io.github.abrar118.matbank.ui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Date and time formats used across the UI. */
public final class Formats {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    public static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);
    public static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    public static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

    private Formats() {
    }

    public static String dateTime(Instant instant, ZoneId zone) {
        return DATE_TIME.format(instant.atZone(zone));
    }

    public static String date(Instant instant, ZoneId zone) {
        return DATE.format(instant.atZone(zone));
    }

    public static String date(LocalDate date) {
        return date == null ? "-" : DATE.format(date);
    }

    /** "Today, 3:41 PM", "Yesterday, 9:02 AM" or "4 Oct 2026, 1:15 PM". */
    public static String friendly(Instant instant, ZoneId zone, LocalDate today) {
        LocalDate day = LocalDate.ofInstant(instant, zone);
        if (day.equals(today)) {
            return "Today, " + TIME.format(instant.atZone(zone));
        }
        if (day.equals(today.minusDays(1))) {
            return "Yesterday, " + TIME.format(instant.atZone(zone));
        }
        return dateTime(instant, zone);
    }
}
