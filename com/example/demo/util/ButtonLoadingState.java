package com.example.demo.util;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ProgressIndicator;

public final class ButtonLoadingState implements AutoCloseable {
    private final ButtonBase button;
    private final String text;
    private final Node graphic;
    private final boolean disabled;

    private ButtonLoadingState(ButtonBase button, String loadingText) {
        this.button = button;
        this.text = button == null ? "" : button.getText();
        this.graphic = button == null ? null : button.getGraphic();
        this.disabled = button != null && button.isDisable();

        if (button != null) {
            ProgressIndicator indicator = new ProgressIndicator();
            indicator.setPrefSize(14, 14);
            indicator.setMaxSize(14, 14);
            button.setGraphic(indicator);
            button.setText(loadingText == null || loadingText.isBlank() ? "Loading..." : loadingText);
            button.setDisable(true);
        }
    }

    public static ButtonLoadingState start(ButtonBase button, String loadingText) {
        return new ButtonLoadingState(button, loadingText);
    }

    @Override
    public void close() {
        if (button == null) {
            return;
        }

        Runnable restore = () -> {
            button.setText(text);
            button.setGraphic(graphic);
            button.setDisable(disabled);
        };

        if (Platform.isFxApplicationThread()) {
            restore.run();
        } else {
            Platform.runLater(restore);
        }
    }
}
