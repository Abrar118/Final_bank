package io.github.abrar118.matbank.ui.components;

import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.domain.LedgerEntry;
import io.github.abrar118.matbank.domain.TxKind;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.time.LocalDate;
import java.time.ZoneId;

/** One transaction in a list: direction icon, description, note, time and signed amount. */
public final class LedgerRow {

    private LedgerRow() {
    }

    public static Node of(LedgerEntry e, ZoneId zone, LocalDate today) {
        StackPane badge = new StackPane(Ui.icon(iconFor(e.kind()), 18));
        badge.getStyleClass().addAll("tx-badge", styleFor(e.kind()));
        badge.setMinSize(38, 38);
        badge.setMaxSize(38, 38);

        // Deposits read best by their reference ("Salary - Padma Software"); everything else by description.
        boolean depositWithNote = e.kind() == TxKind.DEPOSIT && e.note() != null;
        Label title = Ui.label(depositWithNote ? e.note() : e.description(), Styles.TEXT_BOLD);
        StringBuilder detail = new StringBuilder(Formats.friendly(e.createdAt(), zone, today));
        String extra = depositWithNote ? e.description() : e.note();
        if (extra != null) {
            detail.append("  ·  ").append(extra);
        }
        Label sub = Ui.label(detail.toString(), Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        sub.setMinWidth(0);
        VBox text = new VBox(2, title, sub);
        text.setMinWidth(0);

        Label amount = Ui.amount(e.amount());
        Label account = Ui.label(e.accountType().label(), Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        VBox right = new VBox(2, amount, account);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setMinWidth(Region.USE_PREF_SIZE);

        HBox row = new HBox(12, badge, text, Ui.spacer(), right);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 4, 8, 4));
        row.getStyleClass().add("ledger-row");
        return row;
    }

    public static Ikon iconFor(TxKind kind) {
        return switch (kind) {
            case DEPOSIT -> Material2OutlinedAL.ACCOUNT_BALANCE_WALLET;
            case OPENING_BALANCE -> Material2OutlinedAL.ACCOUNT_BALANCE;
            case TRANSFER_IN -> Material2OutlinedMZ.SOUTH_WEST;
            case TRANSFER_OUT -> Material2OutlinedMZ.NORTH_EAST;
            case INTERNAL_IN, INTERNAL_OUT -> Material2OutlinedMZ.SWAP_HORIZ;
            case FEE -> Material2OutlinedMZ.RECEIPT;
        };
    }

    public static String styleFor(TxKind kind) {
        return switch (kind.category()) {
            case INCOMING, DEPOSIT -> "in";
            case OUTGOING -> "out";
            case INTERNAL -> "internal";
            case FEE -> "fee";
        };
    }
}
