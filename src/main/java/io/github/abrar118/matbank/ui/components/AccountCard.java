package io.github.abrar118.matbank.ui.components;

import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.AccountType;
import io.github.abrar118.matbank.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** A debit-card style tile for an account: gold for checking and silver for savings, like the 2022 dashboard. */
public final class AccountCard {

    private AccountCard() {
    }

    public static Node of(Account account, String holder) {
        boolean checking = account.type() == AccountType.CHECKING;
        Label type = Ui.label(account.type().label().toUpperCase() + (checking ? "  ·  GOLD" : "  ·  SILVER"),
                "card-type");
        HBox top = new HBox(Ui.wordmark(16), Ui.spacer(), type);
        top.setAlignment(Pos.CENTER_LEFT);

        Region chip = new Region();
        chip.getStyleClass().add("card-chip");
        chip.setPrefSize(38, 28);
        chip.setMaxSize(38, 28);

        Label balanceCaption = Ui.label("Available balance", "card-caption");
        Label balance = Ui.label(account.balance().format(), "card-balance");
        VBox middle = new VBox(2, balanceCaption, balance);

        Label number = Ui.label(account.number(), "card-number");
        Label name = Ui.label(holder.toUpperCase(), "card-holder");
        VBox bottomText = new VBox(2, number, name);
        HBox bottom = new HBox(bottomText, Ui.spacer(), Ui.imageView("visa.png", 58));
        bottom.setAlignment(Pos.BOTTOM_LEFT);

        VBox card = new VBox(12, top, chip, middle, Ui.spacer(), bottom);
        card.getStyleClass().addAll("bank-card", checking ? "gold" : "silver");
        card.setPadding(new Insets(18, 20, 16, 20));
        card.setPrefSize(340, 206);
        card.setMinSize(300, 206);
        card.setMaxHeight(206);
        return card;
    }
}
