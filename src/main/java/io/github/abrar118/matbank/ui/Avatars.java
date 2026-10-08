package io.github.abrar118.matbank.ui;

import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.ProfileService;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Round profile pictures, falling back to coloured initials (the 2022 app downloaded a stock avatar). */
public final class Avatars {

    private static final Color[] PALETTE = {
            Color.web("#3f48cc"), Color.web("#0f766e"), Color.web("#b45309"), Color.web("#be185d"),
            Color.web("#6d28d9"), Color.web("#15803d"), Color.web("#1d4ed8"), Color.web("#b91c1c")};

    private final ProfileService profiles;
    private final Map<Long, Optional<Image>> cache = new ConcurrentHashMap<>();

    public Avatars(ProfileService profiles) {
        this.profiles = profiles;
    }

    public StackPane of(User user, double diameter) {
        return of(user.id(), user.fullName(), user.initials(), diameter);
    }

    public StackPane of(long userId, String name, String initials, double diameter) {
        Circle circle = new Circle(diameter / 2);
        StackPane pane = new StackPane(circle);
        pane.getStyleClass().add("avatar");
        pane.setMinSize(diameter, diameter);
        pane.setMaxSize(diameter, diameter);
        pane.setAlignment(Pos.CENTER);

        Optional<Image> image = cache.computeIfAbsent(userId, id -> profiles.avatar(id)
                .map(bytes -> new Image(new ByteArrayInputStream(bytes)))
                .filter(img -> !img.isError()));
        if (image.isPresent()) {
            circle.setFill(new ImagePattern(image.get()));
        } else {
            circle.setFill(colorFor(name));
            Label text = new Label(initials);
            text.getStyleClass().add("avatar-initials");
            text.setStyle("-fx-font-size: " + Math.round(diameter * 0.38) + "px;");
            pane.getChildren().add(text);
        }
        return pane;
    }

    /** Avatar for someone who may not have an account (e.g. a visitor message). */
    public StackPane forName(String name, double diameter) {
        String[] parts = name == null ? new String[0] : name.strip().split("\\s+");
        String initials = parts.length == 0 || parts[0].isEmpty() ? "?"
                : (parts[0].substring(0, 1) + (parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : ""))
                .toUpperCase();
        Circle circle = new Circle(diameter / 2, colorFor(name == null ? "?" : name));
        Label text = new Label(initials);
        text.getStyleClass().add("avatar-initials");
        text.setStyle("-fx-font-size: " + Math.round(diameter * 0.38) + "px;");
        StackPane pane = new StackPane(circle, text);
        pane.setMinSize(diameter, diameter);
        pane.setMaxSize(diameter, diameter);
        return pane;
    }

    public void invalidate(long userId) {
        cache.remove(userId);
    }

    private static Color colorFor(String name) {
        return PALETTE[Math.floorMod(name.hashCode(), PALETTE.length)];
    }
}
