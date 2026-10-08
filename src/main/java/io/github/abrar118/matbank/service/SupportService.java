package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.db.Database;
import io.github.abrar118.matbank.db.SupportRepository;
import io.github.abrar118.matbank.domain.Feedback;
import io.github.abrar118.matbank.domain.SupportMessage;
import io.github.abrar118.matbank.domain.User;

import java.time.Clock;
import java.util.List;
import java.util.OptionalDouble;

/** The help center: messages to admins (anyone can send one) and star-rated feedback (signed-in users). */
public final class SupportService {

    public static final int MAX_MESSAGE_LENGTH = 2000;

    private final Database db;
    private final SupportRepository support;
    private final Clock clock;

    public SupportService(Database db, SupportRepository support, Clock clock) {
        this.db = db;
        this.support = support;
        this.clock = clock;
    }

    /**
     * @param sender the signed-in user, or {@code null} for a visitor, who must then give a name
     */
    public void sendMessage(User sender, String visitorName, String visitorEmail, String body) {
        String text = Validation.maxLength(Validation.required(body, "Message"), MAX_MESSAGE_LENGTH, "Message");
        if (sender != null) {
            db.inTransaction(c -> support.insertMessage(c, sender.id(), sender.fullName(), sender.email(), text,
                    clock.instant()));
            return;
        }
        String name = Validation.maxLength(Validation.required(visitorName, "Your name"), 80, "Your name");
        String email = Validation.optional(visitorEmail) == null ? null : Validation.email(visitorEmail);
        db.inTransaction(c -> support.insertMessage(c, null, name, email, text, clock.instant()));
    }

    public void submitFeedback(User user, int rating, String body) {
        if (user == null) {
            throw new BankException(BankException.Reason.NOT_ALLOWED, "Sign in to leave feedback");
        }
        if (rating < 1 || rating > 5) {
            throw BankException.validation("Choose a rating from 1 to 5 stars");
        }
        String text = Validation.maxLength(Validation.required(body, "Feedback"), MAX_MESSAGE_LENGTH, "Feedback");
        db.inTransaction(c -> support.insertFeedback(c, user.id(), user.fullName(), rating, text, clock.instant()));
    }

    public List<SupportMessage> messages(User admin) {
        AuthService.requireAdmin(admin);
        return db.read(support::messages);
    }

    public void markRead(User admin, long messageId, boolean read) {
        AuthService.requireAdmin(admin);
        db.write(c -> support.markRead(c, messageId, read));
    }

    public long unreadCount() {
        return db.read(support::unreadCount);
    }

    public List<Feedback> feedback(User admin) {
        AuthService.requireAdmin(admin);
        return db.read(support::feedback);
    }

    public OptionalDouble averageRating() {
        return db.read(support::feedback).stream().mapToInt(Feedback::rating).average();
    }
}
