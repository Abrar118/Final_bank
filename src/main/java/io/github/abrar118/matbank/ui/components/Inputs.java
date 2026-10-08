package io.github.abrar118.matbank.ui.components;

import atlantafx.base.controls.CustomTextField;
import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Ui;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextFormatter;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/** Form controls shared by the money screens. */
public final class Inputs {

    private Inputs() {
    }

    /** Amount field that only accepts digits, commas and one decimal point. */
    public static CustomTextField money() {
        CustomTextField field = new CustomTextField();
        field.setPromptText("0.00");
        Label currency = Ui.label(Money.CURRENCY, "field-prefix");
        field.setLeft(currency);
        field.setTextFormatter(new TextFormatter<>(change ->
                change.getControlNewText().matches("[0-9,]*(\\.[0-9]{0,2})?") ? change : null));
        return field;
    }

    /** Date picker showing "8 Oct 2026" instead of the locale's numeric format. */
    public static DatePicker date(LocalDate value) {
        DatePicker picker = new DatePicker(value);
        picker.setConverter(new StringConverter<>() {
            @Override
            public String toString(LocalDate date) {
                return date == null ? "" : Formats.DATE.format(date);
            }

            @Override
            public LocalDate fromString(String text) {
                if (text == null || text.isBlank()) {
                    return null;
                }
                try {
                    return LocalDate.parse(text.strip(), Formats.DATE);
                } catch (DateTimeParseException e) {
                    return picker.getValue();
                }
            }
        });
        picker.setPromptText("d MMM yyyy");
        return picker;
    }

    /** Parsed amount, or empty if blank or invalid. */
    public static Optional<Money> amountOf(CustomTextField field) {
        try {
            return Optional.of(Money.parse(field.getText()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Account chooser showing type and balance, e.g. "Checking  ·  BDT 12,000.00". */
    public static ComboBox<Account> accounts(List<Account> accounts) {
        ComboBox<Account> box = new ComboBox<>();
        box.getItems().setAll(accounts);
        StringConverter<Account> converter = new StringConverter<>() {
            @Override
            public String toString(Account a) {
                return a == null ? "" : a.type().label() + "  ·  " + a.balance().format();
            }

            @Override
            public Account fromString(String s) {
                return null;
            }
        };
        box.setConverter(converter);
        box.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Account item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : converter.toString(item) + "   (" + item.maskedNumber() + ")");
            }
        });
        if (!accounts.isEmpty()) {
            box.getSelectionModel().selectFirst();
        }
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }
}
