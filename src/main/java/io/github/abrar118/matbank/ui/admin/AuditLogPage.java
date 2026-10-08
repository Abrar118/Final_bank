package io.github.abrar118.matbank.ui.admin;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.AuditEvent;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import io.github.abrar118.matbank.ui.Workspace;
import io.github.abrar118.matbank.ui.components.Tables;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.util.List;

/** Who did what: client accounts opened and closed, password resets, lockouts and profile changes. */
public final class AuditLogPage implements Page {

    private final Workspace workspace;
    private final AppContext ctx;

    public AuditLogPage(Workspace workspace) {
        this.workspace = workspace;
        this.ctx = workspace.context();
    }

    /** Human wording for an audit action code. */
    public static String describe(String action) {
        return switch (action) {
            case "CLIENT_CREATED" -> "Client account opened";
            case "CLIENT_DELETED" -> "Client account closed";
            case "PASSWORD_RESET" -> "Temporary password issued";
            case "PASSWORD_CHANGED" -> "Password changed";
            case "PIN_CHANGED" -> "Admin PIN changed";
            case "PROFILE_UPDATED" -> "Profile updated";
            case "ACCOUNT_LOCKED" -> "Account locked";
            case "ADMIN_CREATED" -> "Admin created";
            default -> action;
        };
    }

    private static String styleFor(String action) {
        return switch (action) {
            case "CLIENT_DELETED", "ACCOUNT_LOCKED" -> Styles.DANGER;
            case "PASSWORD_RESET" -> Styles.WARNING;
            default -> Styles.ACCENT;
        };
    }

    private static Node details(AuditEvent e) {
        Label label = Ui.label(e.details() == null ? "" : e.details());
        label.setTooltip(new Tooltip(label.getText()));
        return label;
    }

    @Override
    public Node view() {
        List<AuditEvent> events = ctx.admin().auditLog(workspace.user(), 1000);
        TableView<AuditEvent> table = new TableView<>(FXCollections.observableArrayList(events));

        table.getColumns().setAll(List.of(
                Tables.<AuditEvent>column("Time", 170,
                        e -> Ui.label(Formats.dateTime(e.createdAt(), ctx.clock().getZone()))),
                Tables.<AuditEvent>column("By", 170, e -> Ui.label(e.actorEmail() == null ? "System" : e.actorEmail())),
                Tables.<AuditEvent>column("Action", 190, e -> Ui.chip(describe(e.action()), styleFor(e.action()))),
                Tables.<AuditEvent>column("Details", 520, AuditLogPage::details)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().addAll(Styles.STRIPED, Tweaks.EDGE_TO_EDGE);
        table.setPrefHeight(620);

        return Ui.page(new VBox(20, new VBox(4, Ui.pageTitle("Audit log"),
                Ui.muted("Security-relevant actions, kept for accountability. Entries can't be edited.")),
                Ui.card(table)));
    }
}
