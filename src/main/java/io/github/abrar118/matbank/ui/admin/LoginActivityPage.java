package io.github.abrar118.matbank.ui.admin;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.LoginEvent;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import io.github.abrar118.matbank.ui.Workspace;
import io.github.abrar118.matbank.ui.components.Tables;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.List;

/** Every sign-in attempt, successful or not. The 2022 version only logged successful client sign-ins. */
public final class LoginActivityPage implements Page {

    private static final String ALL = "All outcomes";

    private final Workspace workspace;
    private final AppContext ctx;

    public LoginActivityPage(Workspace workspace) {
        this.workspace = workspace;
        this.ctx = workspace.context();
    }

    @Override
    public Node view() {
        List<LoginEvent> events = ctx.admin().loginActivity(workspace.user(), 1000);
        TableView<LoginEvent> table = new TableView<>(FXCollections.observableArrayList(events));
        table.getColumns().setAll(List.of(
                Tables.<LoginEvent>column("Time", 190,
                        e -> Ui.label(Formats.dateTime(e.createdAt(), ctx.clock().getZone()))),
                Tables.<LoginEvent>column("Account", 340, this::account),
                Tables.<LoginEvent>column("Outcome", 200, e -> Ui.chip(e.outcome().label(), switch (e.outcome()) {
                    case SUCCESS -> Styles.SUCCESS;
                    case WRONG_CREDENTIALS, UNKNOWN_USER -> Styles.WARNING;
                    case LOCKED -> Styles.DANGER;
                }))));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().addAll(Styles.STRIPED, Tweaks.EDGE_TO_EDGE);
        table.setPrefHeight(620);

        ComboBox<Object> filter = new ComboBox<>();
        filter.getItems().add(ALL);
        filter.getItems().addAll((Object[]) LoginEvent.Outcome.values());
        filter.setValue(ALL);
        filter.setConverter(new StringConverter<>() {
            @Override
            public String toString(Object o) {
                return o instanceof LoginEvent.Outcome outcome ? outcome.label() : String.valueOf(o);
            }

            @Override
            public Object fromString(String s) {
                return s;
            }
        });
        filter.valueProperty().addListener((obs, o, n) -> table.setItems(FXCollections.observableArrayList(
                n instanceof LoginEvent.Outcome wanted
                        ? events.stream().filter(e -> e.outcome() == wanted).toList()
                        : events)));

        long failures = events.stream().filter(e -> e.outcome() != LoginEvent.Outcome.SUCCESS).count();
        HBox bar = new HBox(12, Ui.muted(events.size() + " attempts, " + failures + " unsuccessful"), Ui.spacer(),
                filter);
        bar.setAlignment(Pos.CENTER_LEFT);
        return Ui.page(new VBox(20, new VBox(4, Ui.pageTitle("Sign-in activity"),
                Ui.muted("Successful and failed sign-ins for every account, newest first.")),
                Ui.card(bar, table)));
    }

    private Node account(LoginEvent e) {
        String name = e.userName() != null ? e.userName() : e.email();
        HBox row = new HBox(10, workspace.window().avatars().forName(name, 28),
                new VBox(1, Ui.label(name, Styles.TEXT_BOLD), Ui.label(e.email(), Styles.TEXT_MUTED, Styles.TEXT_SMALL)));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
