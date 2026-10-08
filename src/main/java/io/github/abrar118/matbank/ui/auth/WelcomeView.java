package io.github.abrar118.matbank.ui.auth;

import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.ui.AppWindow;
import io.github.abrar118.matbank.ui.Ui;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.ArrayList;
import java.util.List;

/** Landing screen: the 2022 slideshow and "latest update" panel, rebuilt. */
public final class WelcomeView {

    private static final String[] SLIDES = {"slides/slide-4.jpg", "slides/slide-2.jpg", "slides/slide-1.jpg",
            "slides/slide-3.jpg"};

    private final AppWindow window;

    public WelcomeView(AppWindow window) {
        this.window = window;
    }

    public Node build() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("welcome");
        root.setTop(header());
        root.setCenter(hero());
        root.setBottom(footer());
        return root;
    }

    private Node header() {
        Button about = Ui.flat("About us", null);
        about.setOnAction(e -> window.showVisitorAbout(window::showWelcome));
        Button contact = Ui.flat("Contact us", null);
        contact.setOnAction(e -> window.showVisitorHelp(window::showWelcome));
        Button theme = Ui.themeToggle(window);
        Button signIn = Ui.primary("Sign in", Material2OutlinedAL.LOGIN);
        signIn.setOnAction(e -> window.showLogin());

        HBox bar = new HBox(10, Ui.imageView("logo.png", 36), Ui.wordmark(22), Ui.spacer(), about, contact, theme,
                signIn);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(18, 40, 18, 40));
        return bar;
    }

    private Node hero() {
        Label eyebrow = Ui.label("WELCOME TO", "eyebrow");
        HBox wordmark = Ui.wordmark(64);
        Label tagline = Ui.wrapped("For keeping your finances swift and efficient.", "hero-tagline");
        Label sub = Ui.wrapped("Ensuring a safe and secured way to manage your money. First built by three MIST "
                + "students in 2022, rebuilt in 2026 with JavaFX 21.", Styles.TEXT_MUTED, "hero-sub");

        Button start = Ui.primary("Get started", Material2OutlinedAL.ARROW_FORWARD);
        start.getStyleClass().add(Styles.LARGE);
        start.setOnAction(e -> window.showLogin());
        Button demo = Ui.secondary("Explore with a demo account", null);
        demo.getStyleClass().add(Styles.LARGE);
        demo.setOnAction(e -> window.showLogin());
        HBox cta = new HBox(12, start, demo);

        HBox features = new HBox(22,
                feature(Material2OutlinedAL.LOCK, "bcrypt-protected sign-in"),
                feature(Material2OutlinedMZ.PICTURE_AS_PDF, "PDF statements"),
                feature(Material2OutlinedMZ.REPEAT, "Scheduled payments"));

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(16, 16);
        HBox preparing = new HBox(8, spinner, Ui.muted("Preparing the demo bank..."));
        preparing.setAlignment(Pos.CENTER_LEFT);
        preparing.visibleProperty().bind(window.readyProperty().not());
        preparing.managedProperty().bind(preparing.visibleProperty());

        VBox text = new VBox(18, eyebrow, wordmark, tagline, sub, cta, features, preparing);
        text.setMaxWidth(560);
        text.setAlignment(Pos.CENTER_LEFT);

        VBox side = new VBox(18, slideshow(), whatsNew());
        side.setAlignment(Pos.CENTER);

        HBox hero = new HBox(56, text, side);
        hero.setAlignment(Pos.CENTER);
        hero.setPadding(new Insets(10, 40, 10, 40));
        HBox.setHgrow(text, Priority.ALWAYS);
        return hero;
    }

    private static Node feature(Ikon ikon, String text) {
        HBox box = new HBox(8, Ui.icon(ikon, 18), Ui.muted(text));
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("feature");
        return box;
    }

    private Node slideshow() {
        double width = 540;
        double height = 300;
        List<Region> slides = new ArrayList<>();
        for (String name : SLIDES) {
            Region slide = new Region();
            slide.setStyle("-fx-background-image: url('" + Ui.image(name).getUrl() + "');"
                    + "-fx-background-size: cover; -fx-background-position: center;");
            slide.setPrefSize(width, height);
            slide.setOpacity(0);
            slides.add(slide);
        }
        slides.getFirst().setOpacity(1);

        HBox dots = new HBox(6);
        dots.setAlignment(Pos.CENTER);
        List<Circle> dotList = new ArrayList<>();
        for (int i = 0; i < slides.size(); i++) {
            Circle dot = new Circle(4);
            dot.getStyleClass().add("slide-dot");
            dotList.add(dot);
            dots.getChildren().add(dot);
        }
        dotList.getFirst().getStyleClass().add("active");
        dots.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(dots, Pos.BOTTOM_CENTER);
        StackPane.setMargin(dots, new Insets(0, 0, 14, 0));

        StackPane stack = new StackPane();
        stack.getChildren().addAll(slides);
        stack.getChildren().add(dots);
        stack.setPrefSize(width, height);
        stack.setMaxSize(width, height);
        Rectangle clip = new Rectangle(width, height);
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        stack.setClip(clip);
        stack.getStyleClass().add("slideshow");

        int[] index = {0};
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(4), e -> {
            int previous = index[0];
            index[0] = (previous + 1) % slides.size();
            fade(slides.get(previous), 0);
            fade(slides.get(index[0]), 1);
            dotList.get(previous).getStyleClass().remove("active");
            dotList.get(index[0]).getStyleClass().add("active");
        }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        stack.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) {
                timeline.stop();
            }
        });
        return stack;
    }

    private static void fade(Node node, double to) {
        FadeTransition fade = new FadeTransition(Duration.millis(700), node);
        fade.setToValue(to);
        fade.play();
    }

    private Node whatsNew() {
        VBox list = new VBox(8,
                update("2026", "Rebuilt on JavaFX 21 with light and dark themes"),
                update("2026", "SQLite storage, PDF statements, scheduled payments"),
                update("2022", "Interest on fixed deposits increased to 10%"),
                update("2022", "Admin list updated for spring 2022"));
        VBox card = Ui.card("Latest updates", null, list);
        card.setPrefWidth(540);
        card.setMaxWidth(540);
        return card;
    }

    private static Node update(String year, String text) {
        Label badge = Ui.chip(year, year.equals("2022") ? null : Styles.ACCENT);
        badge.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(10, badge, Ui.wrapped(text));
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node footer() {
        Button terms = Ui.flat("Terms of usage and privacy policy", Material2OutlinedMZ.POLICY);
        terms.setOnAction(e -> PolicyDialog.show(window));
        HBox bar = new HBox(10, terms, Ui.spacer(), Ui.muted("MAT Bank 2.0  |  Since 2022"));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(12, 40, 18, 32));
        return bar;
    }
}
