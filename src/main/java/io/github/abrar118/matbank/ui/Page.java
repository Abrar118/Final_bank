package io.github.abrar118.matbank.ui;

import javafx.scene.Node;

/** One screen inside the signed-in workspace. Pages are rebuilt on every visit, so they always show fresh data. */
public interface Page {

    Node view();
}
