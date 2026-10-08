package io.github.abrar118.matbank.service;

import io.github.abrar118.matbank.db.AccountRepository;
import io.github.abrar118.matbank.db.Database;
import io.github.abrar118.matbank.db.LedgerRepository;
import io.github.abrar118.matbank.db.UserRepository;
import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.AccountType;
import io.github.abrar118.matbank.domain.LedgerEntry;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.User;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import java.util.List;

/** Builds account statements and renders them as PDF. */
public final class StatementService {

    public record Statement(User client, Account account, LocalDate from, LocalDate to, Money opening,
                            Money moneyIn, Money moneyOut, Money closing, List<LedgerEntry> entries,
                            Instant generatedAt, ZoneId zone) {
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private static final Color BRAND = new Color(0x3F, 0x48, 0xCC);
    private static final Color INK = new Color(0x1F, 0x23, 0x28);
    private static final Color MUTED = new Color(0x65, 0x6D, 0x76);
    private static final Color RULE = new Color(0xD0, 0xD7, 0xDE);
    private static final Color STRIPE = new Color(0xF6, 0xF8, 0xFA);
    private static final Color CREDIT = new Color(0x1A, 0x7F, 0x37);

    private static final float MARGIN = 48;
    private static final float ROW_HEIGHT = 18;

    private final Database db;
    private final UserRepository users;
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final Clock clock;

    public StatementService(Database db, UserRepository users, AccountRepository accounts, LedgerRepository ledger,
                            Clock clock) {
        this.db = db;
        this.users = users;
        this.accounts = accounts;
        this.ledger = ledger;
        this.clock = clock;
    }

    public Statement build(long userId, AccountType type, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw BankException.validation("Choose a start and end date");
        }
        if (from.isAfter(to)) {
            throw BankException.validation("The start date must be on or before the end date");
        }
        if (to.isAfter(LocalDate.now(clock))) {
            to = LocalDate.now(clock);
        }
        ZoneId zone = clock.getZone();
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        LocalDate last = to;
        return db.read(c -> {
            User client = users.findById(c, userId).orElseThrow();
            Account account = accounts.find(c, userId, type).orElseThrow(() ->
                    new BankException(BankException.Reason.ACCOUNT_NOT_FOUND, "No " + type.label() + " account"));
            Money opening = ledger.balanceBefore(c, account.id(), start);
            List<LedgerEntry> entries = ledger.forAccount(c, account.id(), start, end);
            Money in = Money.ZERO;
            Money out = Money.ZERO;
            for (LedgerEntry e : entries) {
                if (e.isCredit()) {
                    in = in.plus(e.amount());
                } else {
                    out = out.plus(e.amount().abs());
                }
            }
            Money closing = entries.isEmpty() ? opening : entries.getLast().balanceAfter();
            return new Statement(client, account, from, last, opening, in, out, closing, entries, clock.instant(),
                    zone);
        });
    }

    public void writePdf(Statement statement, Path file) {
        try (OutputStream out = Files.newOutputStream(file)) {
            writePdf(statement, out);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the statement to " + file, e);
        }
    }

    public void writePdf(Statement s, OutputStream out) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDDocumentInformation info = doc.getDocumentInformation();
            info.setTitle("MAT Bank statement " + s.account().number());
            info.setAuthor("MAT Bank");
            info.setCreator("MAT Bank 2.0");
            GregorianCalendar created = new GregorianCalendar();
            created.setTimeInMillis(s.generatedAt().toEpochMilli());
            info.setCreationDate(created);
            new Renderer(doc, s).render();
            doc.save(out);
        }
    }

    /** Lays out the statement page by page. */
    private static final class Renderer {

        private final PDDocument doc;
        private final Statement s;
        private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDImageXObject logo;
        private final float width = PDRectangle.A4.getWidth();
        private final float height = PDRectangle.A4.getHeight();
        // Column x positions: date, description, reference, debit (right edge), credit (right edge), balance (right edge)
        private final float colDate = MARGIN;
        private final float colDesc = MARGIN + 70;
        private final float colRef = MARGIN + 250;
        private final float colDebit = 395;
        private final float colCredit = 470;
        private final float colBalance = width - MARGIN;

        private PDPageContentStream cs;
        private float y;

        Renderer(PDDocument doc, Statement s) throws IOException {
            this.doc = doc;
            this.s = s;
            this.logo = loadLogo(doc);
        }

        void render() throws IOException {
            newPage(true);
            int row = 0;
            for (LedgerEntry e : s.entries()) {
                if (y < MARGIN + 40) {
                    cs.close();
                    newPage(false);
                }
                if (row++ % 2 == 0) {
                    fill(STRIPE, MARGIN - 4, y - 5, width - 2 * MARGIN + 8, ROW_HEIGHT);
                }
                String date = DAY.format(e.createdAt().atZone(s.zone()));
                String description = e.description() + (e.note() == null ? "" : " - " + e.note());
                text(regular, 8.5f, INK, colDate, y, date);
                text(regular, 8.5f, INK, colDesc, y, fit(regular, 8.5f, description, colRef - colDesc - 8));
                text(regular, 8f, MUTED, colRef, y, e.reference());
                if (e.isCredit()) {
                    right(regular, 8.5f, CREDIT, colCredit, y, e.amount().plain());
                } else {
                    right(regular, 8.5f, INK, colDebit, y, e.amount().abs().plain());
                }
                right(bold, 8.5f, INK, colBalance, y, e.balanceAfter().plain());
                y -= ROW_HEIGHT;
            }
            if (s.entries().isEmpty()) {
                text(regular, 10, MUTED, MARGIN, y, "No transactions in this period.");
                y -= ROW_HEIGHT;
            }
            y -= 6;
            line(RULE, MARGIN, y + ROW_HEIGHT - 4, width - MARGIN);
            text(bold, 9, INK, colDesc, y, "Closing balance");
            right(bold, 9, INK, colBalance, y, s.closing().format());
            cs.close();
            footers();
        }

        private void newPage(boolean first) throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            cs = new PDPageContentStream(doc, page);
            y = height - MARGIN;

            if (logo != null) {
                cs.drawImage(logo, MARGIN, y - 30, 34, 34);
            }
            text(bold, 18, BRAND, MARGIN + 44, y - 14, "MAT");
            text(bold, 18, INK, MARGIN + 44 + bold.getStringWidth("MAT ") / 1000 * 18, y - 14, "BANK");
            text(regular, 9, MUTED, MARGIN + 44, y - 28, "Account statement");
            right(bold, 10, INK, width - MARGIN, y - 12, DAY.format(s.from()) + "  to  " + DAY.format(s.to()));
            right(regular, 8.5f, MUTED, width - MARGIN, y - 26,
                    "Generated " + STAMP.format(s.generatedAt().atZone(s.zone())));
            y -= 48;
            line(BRAND, MARGIN, y, width - MARGIN);
            y -= 22;

            if (first) {
                text(bold, 11, INK, MARGIN, y, s.client().fullName());
                text(regular, 9, MUTED, MARGIN, y - 14, s.client().email());
                if (s.client().address() != null) {
                    text(regular, 9, MUTED, MARGIN, y - 27, fit(regular, 9, s.client().address(), 230));
                }
                right(bold, 11, INK, width - MARGIN, y, s.account().type().label() + " account");
                right(regular, 9, MUTED, width - MARGIN, y - 14, "No. " + s.account().number());
                y -= 50;

                float boxWidth = (width - 2 * MARGIN - 3 * 10) / 4;
                String[][] boxes = {
                        {"Opening balance", s.opening().format()},
                        {"Money in", s.moneyIn().format()},
                        {"Money out", s.moneyOut().format()},
                        {"Closing balance", s.closing().format()}};
                for (int i = 0; i < boxes.length; i++) {
                    float x = MARGIN + i * (boxWidth + 10);
                    fill(STRIPE, x, y - 30, boxWidth, 42);
                    text(regular, 8, MUTED, x + 8, y, boxes[i][0]);
                    text(bold, 11, i == 3 ? BRAND : INK, x + 8, y - 18, boxes[i][1]);
                }
                y -= 62;
            }

            text(bold, 8, MUTED, colDate, y, "DATE");
            text(bold, 8, MUTED, colDesc, y, "DESCRIPTION");
            text(bold, 8, MUTED, colRef, y, "REFERENCE");
            right(bold, 8, MUTED, colDebit, y, "DEBIT");
            right(bold, 8, MUTED, colCredit, y, "CREDIT");
            right(bold, 8, MUTED, colBalance, y, "BALANCE");
            y -= 6;
            line(RULE, MARGIN, y, width - MARGIN);
            y -= 14;
            if (first) {
                text(regular, 8.5f, MUTED, colDesc, y, "Balance brought forward");
                right(regular, 8.5f, MUTED, colBalance, y, s.opening().plain());
                y -= ROW_HEIGHT;
            }
        }

        private void footers() throws IOException {
            int total = doc.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                try (PDPageContentStream footer = new PDPageContentStream(doc, doc.getPage(i),
                        PDPageContentStream.AppendMode.APPEND, true)) {
                    cs = footer;
                    line(RULE, MARGIN, MARGIN - 8, width - MARGIN);
                    text(regular, 7.5f, MUTED, MARGIN, MARGIN - 20,
                            "MAT Bank - all amounts in " + Money.CURRENCY + ". Demo application; not a real bank.");
                    right(regular, 7.5f, MUTED, width - MARGIN, MARGIN - 20, "Page " + (i + 1) + " of " + total);
                }
            }
        }

        private void text(PDType1Font font, float size, Color color, float x, float baseline, String value)
                throws IOException {
            cs.beginText();
            cs.setFont(font, size);
            cs.setNonStrokingColor(color);
            cs.newLineAtOffset(x, baseline);
            cs.showText(winAnsi(value));
            cs.endText();
        }

        private void right(PDType1Font font, float size, Color color, float rightEdge, float baseline, String value)
                throws IOException {
            String safe = winAnsi(value);
            text(font, size, color, rightEdge - font.getStringWidth(safe) / 1000 * size, baseline, safe);
        }

        private void line(Color color, float x1, float yy, float x2) throws IOException {
            cs.setStrokingColor(color);
            cs.setLineWidth(0.7f);
            cs.moveTo(x1, yy);
            cs.lineTo(x2, yy);
            cs.stroke();
        }

        private void fill(Color color, float x, float yy, float w, float h) throws IOException {
            cs.setNonStrokingColor(color);
            cs.addRect(x, yy, w, h);
            cs.fill();
        }

        private static String fit(PDType1Font font, float size, String value, float maxWidth) throws IOException {
            String safe = winAnsi(value);
            if (font.getStringWidth(safe) / 1000 * size <= maxWidth) {
                return safe;
            }
            String ellipsis = "...";
            int end = safe.length();
            while (end > 0 && font.getStringWidth(safe.substring(0, end) + ellipsis) / 1000 * size > maxWidth) {
                end--;
            }
            return safe.substring(0, end) + ellipsis;
        }

        /** The standard PDF fonts only cover Windows-1252; replace anything else so rendering can't fail. */
        private static String winAnsi(String value) {
            StringBuilder sb = new StringBuilder(value.length());
            for (char ch : value.toCharArray()) {
                boolean printableAscii = ch >= 0x20 && ch < 0x7F;
                boolean latin1 = ch >= 0xA0 && ch <= 0xFF;
                sb.append(printableAscii || latin1 ? ch : '?');
            }
            return sb.toString();
        }

        private static PDImageXObject loadLogo(PDDocument doc) {
            try (InputStream in = StatementService.class.getResourceAsStream("/io/github/abrar118/matbank/images/logo.png")) {
                return in == null ? null : PDImageXObject.createFromByteArray(doc, in.readAllBytes(), "logo");
            } catch (IOException e) {
                return null;
            }
        }
    }
}
