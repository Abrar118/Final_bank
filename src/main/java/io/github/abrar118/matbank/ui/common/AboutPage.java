package io.github.abrar118.matbank.ui.common;

import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.ui.AppWindow;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import io.github.abrar118.matbank.ui.auth.PolicyDialog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

/** The story of the app and the three people who built it in 2022. */
public final class AboutPage implements Page {

    private record Member(String name, String photo) {
    }

    private static final Member[] TEAM = {
            new Member("Abrar Mahir Esam", "team/abrar.jpg"),
            new Member("Mehmil Khan", "team/mehmil.jpg"),
            new Member("Farheen Mahjarin Trisha", "team/trisha.jpg")};

    private static final String[][] THEN_AND_NOW = {
            {"Platform", "JDK 19, JavaFX 19 early access", "JDK 25 LTS, JavaFX 25 LTS"},
            {"Storage", "Text files in ~/Music/Data", "SQLite with versioned migrations"},
            {"Money", "double, rounded on screen", "Exact integer poisha, one ledger"},
            {"Passwords", "Plain text", "bcrypt hashes, lockout, audit log"},
            {"Forgot password", "Email OTP via a hard-coded Gmail login", "Admin-issued temporary password"},
            {"Windows", "A new window per screen", "One window, light and dark themes"},
            {"New in 2.0", "-", "PDF statements, scheduled payments, live rates, search"}};

    private final AppWindow window;

    public AboutPage(AppWindow window) {
        this.window = window;
    }

    @Override
    public Node view() {
        VBox story = Ui.card(
                new HBox(14, Ui.imageView("logo.png", 56), new VBox(4, Ui.wordmark(30),
                        Ui.muted("Developed by students of the Department of Computer Science and Engineering, "
                                + "Military Institute of Science and Technology (MIST)."))),
                Ui.wrapped("MAT Bank began in 2022 as a JavaFX course project built with IntelliJ and Scene Builder. "
                        + "In 2026 it was rebuilt from the ground up as a tribute: the same screens and ideas, "
                        + "with the foundations of a modern desktop app."));

        HBox team = new HBox(16);
        for (Member m : TEAM) {
            team.getChildren().add(member(m));
        }

        GridPane table = new GridPane();
        table.setHgap(24);
        table.setVgap(10);
        table.add(Ui.label("", Styles.TEXT_MUTED), 0, 0);
        table.add(Ui.label("2022", Styles.TEXT_BOLD), 1, 0);
        table.add(Ui.label("2026", Styles.TEXT_BOLD), 2, 0);
        for (int i = 0; i < THEN_AND_NOW.length; i++) {
            Label topic = Ui.label(THEN_AND_NOW[i][0], Styles.TEXT_MUTED);
            topic.setMinWidth(Region.USE_PREF_SIZE);
            table.add(topic, 0, i + 1);
            table.add(Ui.wrapped(THEN_AND_NOW[i][1]), 1, i + 1);
            table.add(Ui.wrapped(THEN_AND_NOW[i][2]), 2, i + 1);
        }
        VBox thenNow = Ui.card("Then and now", null, table);
        HBox.setHgrow(thenNow, Priority.ALWAYS);

        ImageView legacy = Ui.imageView("legacy-welcome-2022.jpg", 380);
        Rectangle clip = new Rectangle(380, 225);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        legacy.setClip(clip);
        VBox legacyCard = Ui.card("The 2022 welcome screen", null, legacy,
                Ui.muted("Preserved on the legacy-2022 branch."));
        legacyCard.setMinWidth(420);

        Button github = Ui.secondary("Source on GitHub", Material2OutlinedAL.CODE);
        github.setOnAction(e -> Ui.open("https://github.com/Abrar118/Final_bank"));
        Button policy = Ui.flat("Terms of usage and privacy policy", Material2OutlinedMZ.POLICY);
        policy.setOnAction(e -> PolicyDialog.show(window));
        HBox actions = new HBox(10, github, policy);

        return Ui.page(new VBox(20, story, Ui.sectionTitle("Developed by"), team, new HBox(20, thenNow, legacyCard),
                actions));
    }

    private static Node member(Member m) {
        Circle photo = new Circle(54);
        photo.setFill(new ImagePattern(Ui.image(m.photo())));
        StackPane ring = new StackPane(photo);
        ring.getStyleClass().add("team-photo");
        ring.setPadding(new Insets(4));
        ring.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Label name = Ui.label(m.name(), Styles.TITLE_4);
        name.setWrapText(true);
        name.setAlignment(Pos.CENTER);
        VBox card = Ui.card(ring, name, Ui.muted("CSE, MIST"));
        card.setAlignment(Pos.CENTER);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }
}
