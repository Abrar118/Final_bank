package io.github.abrar118.matbank;

import io.github.abrar118.matbank.ui.MatBankApp;
import javafx.application.Application;

/**
 * Plain main class. Launching through a class that doesn't extend {@link Application} lets the app start from a
 * classpath jar without JavaFX complaining about missing runtime components.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(MatBankApp.class, args);
    }
}
