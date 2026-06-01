package com.example.demo.factory;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.Objects;

public class SharedUiFactory {
    private static final String DELETE_ICON_PATH = "/com/example/demo/images/delete.png";
    private static final String SETTINGS_ICON_PATH = "/com/example/demo/images/settings.png";

    public static Button createDeleteIconButton(String tooltipText) {
        Button button = new Button();
        button.getStyleClass().add("mapping-delete-button");
        button.setTooltip(new Tooltip(tooltipText));

        Image icon = null;
        try {
            icon = new Image(Objects.requireNonNull(SharedUiFactory.class.getResourceAsStream(DELETE_ICON_PATH)));
        } catch (Exception ignored) {
        }

        if (icon != null && !icon.isError()) {
            ImageView imageView = new ImageView(icon);
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(14);
            imageView.setFitHeight(14);
            button.setGraphic(imageView);
        } else {
            button.setText("X");
        }

        return button;
    }

    public static Button createEditUserButton() {
        Button button = new Button();
        button.getStyleClass().add("sidebar-user-settings-button");
        button.setTooltip(new Tooltip("Edit user name"));

        Image icon = null;
        try {
            icon = new Image(Objects.requireNonNull(SharedUiFactory.class.getResourceAsStream(SETTINGS_ICON_PATH)));
        } catch (Exception ignored) {
            // Fallback text keeps control usable if image is missing from classpath.
        }

        if (icon != null && !icon.isError()) {
            ImageView imageView = new ImageView(icon);
            imageView.setPreserveRatio(true);
            imageView.setFitWidth(14);
            imageView.setFitHeight(14);
            button.setGraphic(imageView);
        } else {
            button.setText("Edit");
        }

        return button;
    }

    public static StackPane createLoadingOverlay(String message) {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setPrefSize(36, 36);

        Label label = new Label(message);
        label.setStyle("-fx-text-fill: #374151; -fx-font-size: 12px;");

        VBox content = new VBox(8, indicator, label);
        content.setAlignment(Pos.CENTER);

        StackPane overlay = new StackPane(content);
        overlay.setStyle("-fx-background-color: rgba(255,255,255,0.75);");
        overlay.setVisible(false);
        overlay.setManaged(false);
        return overlay;
    }
}