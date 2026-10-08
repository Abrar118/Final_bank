package io.github.abrar118.matbank.ui;

import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.domain.Money;
import javafx.application.HostServices;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.Objects;

/** Small factory methods that keep the views short and visually consistent. */
public final class Ui {

    private static final String IMAGES = "/io/github/abrar118/matbank/images/";
    private static HostServices hostServices;

    private Ui() {
    }

    static void setHostServices(HostServices services) {
        hostServices = services;
    }

    /** Opens a link or file in the system browser or viewer. */
    public static void open(String uri) {
        if (hostServices != null) {
            hostServices.showDocument(uri);
        }
    }

    public static FontIcon icon(Ikon ikon) {
        return icon(ikon, 18);
    }

    public static FontIcon icon(Ikon ikon, int size) {
        FontIcon icon = new FontIcon(ikon);
        icon.setIconSize(size);
        return icon;
    }

    /** Loads a bundled image synchronously; they're small, and image patterns need fully loaded images. */
    public static Image image(String name) {
        return new Image(Objects.requireNonNull(Ui.class.getResource(IMAGES + name), name).toExternalForm());
    }

    /** Sun/moon button that switches theme and keeps its icon in sync with the current theme. */
    public static Button themeToggle(AppWindow window) {
        Button button = iconButton(Material2OutlinedMZ.NIGHTS_STAY, "Switch light/dark theme");
        Runnable sync = () -> button.setGraphic(icon(Theme.current() == Theme.DARK
                ? Material2OutlinedMZ.WB_SUNNY : Material2OutlinedMZ.NIGHTS_STAY, 18));
        sync.run();
        // Weak so old buttons (e.g. from a previous sign-in) can be garbage collected; the button keeps the
        // listener alive for as long as it exists.
        ChangeListener<Theme> listener = (obs, o, n) -> sync.run();
        button.getProperties().put("theme-listener", listener);
        Theme.currentProperty().addListener(new WeakChangeListener<>(listener));
        button.setOnAction(e -> window.toggleTheme());
        return button;
    }

    public static ImageView imageView(String name, double fitWidth) {
        ImageView view = new ImageView(image(name));
        view.setPreserveRatio(true);
        view.setSmooth(true);
        view.setFitWidth(fitWidth);
        return view;
    }

    public static Label label(String text, String... styleClasses) {
        Label label = new Label(text);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    public static Label wrapped(String text, String... styleClasses) {
        Label label = label(text, styleClasses);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    public static Label muted(String text) {
        return label(text, Styles.TEXT_MUTED);
    }

    public static Label pageTitle(String text) {
        return label(text, Styles.TITLE_2);
    }

    public static Label sectionTitle(String text) {
        return label(text, Styles.TITLE_4);
    }

    /** A rounded panel with the theme's surface colour and border. */
    public static VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("panel");
        box.setPadding(new Insets(20));
        return box;
    }

    /** Card with a title row and optional trailing node (e.g. a link button). */
    public static VBox card(String title, Node trailing, Node... body) {
        HBox header = new HBox(8, sectionTitle(title), spacer());
        header.setAlignment(Pos.CENTER_LEFT);
        if (trailing != null) {
            header.getChildren().add(trailing);
        }
        VBox box = card(header);
        box.getChildren().addAll(body);
        return box;
    }

    public static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        VBox.setVgrow(region, Priority.ALWAYS);
        return region;
    }

    /** Label above a control, with an optional hint below. */
    public static VBox field(String label, Node control, String hint) {
        VBox box = new VBox(6, label(label, "field-label"), control);
        if (hint != null) {
            box.getChildren().add(label(hint, Styles.TEXT_MUTED, Styles.TEXT_SMALL));
        }
        if (control instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
        }
        return box;
    }

    public static VBox field(String label, Node control) {
        return field(label, control, null);
    }

    public static Button primary(String text, Ikon ikon) {
        Button button = new Button(text, ikon == null ? null : icon(ikon, 16));
        button.getStyleClass().add(Styles.ACCENT);
        button.setDefaultButton(false);
        return button;
    }

    public static Button secondary(String text, Ikon ikon) {
        return new Button(text, ikon == null ? null : icon(ikon, 16));
    }

    public static Button flat(String text, Ikon ikon) {
        Button button = new Button(text, ikon == null ? null : icon(ikon, 16));
        button.getStyleClass().add(Styles.FLAT);
        return button;
    }

    public static Button iconButton(Ikon ikon, String tooltip) {
        Button button = new Button(null, icon(ikon, 18));
        button.getStyleClass().addAll(Styles.BUTTON_ICON, Styles.FLAT);
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        return button;
    }

    public static Button link(String text, String url) {
        Button button = flat(text, null);
        button.getStyleClass().add("link-button");
        button.setOnAction(e -> open(url));
        return button;
    }

    /** Coloured pill, e.g. "Active" or "Locked". Variant is one of the AtlantaFX styles (success, danger...). */
    public static Label chip(String text, String variant) {
        Label chip = label(text, "chip");
        if (variant != null) {
            chip.getStyleClass().add(variant);
        }
        return chip;
    }

    /** Amount coloured green for credits and default for debits, e.g. "+BDT 500.00". */
    public static Label amount(Money amount) {
        Label label = label(amount.formatSigned(), "amount");
        label.getStyleClass().add(amount.isPositive() ? "credit" : "debit");
        return label;
    }

    /** Scrollable page body with consistent padding. */
    public static ScrollPane page(Node content) {
        VBox wrapper = new VBox(content);
        wrapper.getStyleClass().add("page");
        wrapper.setPadding(new Insets(24, 28, 32, 28));
        wrapper.setFillWidth(true);
        ScrollPane scroll = new ScrollPane(wrapper);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().addAll("page-scroll", Styles.FLAT);
        return scroll;
    }

    /** Big number tile used on dashboards. */
    public static VBox stat(Ikon ikon, String caption, String value, String footnote) {
        HBox top = new HBox(8, icon(ikon, 18), label(caption, Styles.TEXT_MUTED));
        top.setAlignment(Pos.CENTER_LEFT);
        Label number = label(value, "stat-value");
        VBox tile = new VBox(6, top, number);
        if (footnote != null) {
            tile.getChildren().add(label(footnote, Styles.TEXT_MUTED, Styles.TEXT_SMALL));
        }
        tile.getStyleClass().addAll("panel", "stat-tile");
        tile.setPadding(new Insets(16, 18, 16, 18));
        HBox.setHgrow(tile, Priority.ALWAYS);
        tile.setMaxWidth(Double.MAX_VALUE);
        return tile;
    }

    /** Placeholder for empty lists. */
    public static VBox empty(Ikon ikon, String title, String hint) {
        VBox box = new VBox(8, icon(ikon, 36), label(title, Styles.TITLE_4), muted(hint));
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(32));
        box.getStyleClass().add("empty-state");
        return box;
    }

    /** Two-letter MAT/BANK wordmark in brand colours. */
    public static HBox wordmark(int size) {
        Label mat = label("MAT", "wordmark-mat");
        Label bank = label("BANK", "wordmark-bank");
        mat.setStyle("-fx-font-size: " + size + "px;");
        bank.setStyle("-fx-font-size: " + size + "px;");
        HBox box = new HBox(Math.max(2, size / 6.0), mat, bank);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
