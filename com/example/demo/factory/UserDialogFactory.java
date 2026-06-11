package com.example.demo.factory;

import com.example.demo.model.User;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.util.Optional;

public final class UserDialogFactory {
    private UserDialogFactory() {
    }

    public static Optional<AddUserInput> showAddUserDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add User");
        dialog.setHeaderText("Create a new user.");

        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        TextField userIdField = createField("User ID");
        TextField firstNameField = createField("First Name");
        TextField lastNameField = createField("Last Name");

        dialog.getDialogPane().setContent(createContent(
            new Label("User ID"),
            userIdField,
            new Label("First Name"),
            firstNameField,
            new Label("Last Name"),
            lastNameField
        ));

        Node addButtonNode = dialog.getDialogPane().lookupButton(addButtonType);
        if (!(addButtonNode instanceof Button addButton)) {
            return Optional.empty();
        }

        Runnable updateAddState = () -> {
            String userIdText = trimmed(userIdField);
            addButton.setDisable(!isPositiveIntegerText(userIdText));
        };
        userIdField.textProperty().addListener((obs, oldValue, newValue) -> updateAddState.run());
        firstNameField.textProperty().addListener((obs, oldValue, newValue) -> updateAddState.run());
        lastNameField.textProperty().addListener((obs, oldValue, newValue) -> updateAddState.run());
        updateAddState.run();

        wireEnterToButton(addButton, userIdField, firstNameField, lastNameField);
        Platform.runLater(userIdField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != addButtonType) {
            return Optional.empty();
        }

        return Optional.of(new AddUserInput(
            Integer.parseInt(trimmed(userIdField)),
            trimmed(firstNameField),
            trimmed(lastNameField)
        ));
    }

    public static Optional<EditUserInput> showEditUserDialog(User user) {
        if (user == null) {
            return Optional.empty();
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit User Name");
        dialog.setHeaderText("Update name for user " + user.getUserID() + ".");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField firstNameField = createField("First Name");
        firstNameField.setText(user.getFName());
        TextField lastNameField = createField("Last Name");
        lastNameField.setText(user.getLName());

        dialog.getDialogPane().setContent(createContent(
            new Label("First Name"),
            firstNameField,
            new Label("Last Name"),
            lastNameField
        ));

        Node saveButtonNode = dialog.getDialogPane().lookupButton(saveButtonType);
        if (!(saveButtonNode instanceof Button saveButton)) {
            return Optional.empty();
        }

        Runnable updateSaveState = () -> saveButton.setDisable(
            trimmed(firstNameField).isBlank() || trimmed(lastNameField).isBlank()
        );
        firstNameField.textProperty().addListener((obs, oldValue, newValue) -> updateSaveState.run());
        lastNameField.textProperty().addListener((obs, oldValue, newValue) -> updateSaveState.run());
        updateSaveState.run();

        wireEnterToButton(saveButton, firstNameField, lastNameField);
        Platform.runLater(firstNameField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveButtonType) {
            return Optional.empty();
        }

        return Optional.of(new EditUserInput(trimmed(firstNameField), trimmed(lastNameField)));
    }

    private static TextField createField(String promptText) {
        TextField field = new TextField();
        field.setPromptText(promptText);
        field.setPrefWidth(260);
        return field;
    }

    private static VBox createContent(Node... nodes) {
        VBox content = new VBox(10, nodes);
        content.setPadding(new Insets(12, 12, 4, 12));
        content.setPrefWidth(320);
        return content;
    }

    private static void wireEnterToButton(Button button, TextField... fields) {
        button.setDefaultButton(true);
        Runnable fireIfValid = () -> {
            if (!button.isDisable()) {
                button.fire();
            }
        };

        for (TextField field : fields) {
            field.setOnAction(ignored -> fireIfValid.run());
        }
    }

    private static String trimmed(TextField field) {
        return field.getText() == null ? "" : field.getText().trim();
    }

    private static boolean isPositiveIntegerText(String value) {
        if (value == null || !value.matches("\\d+")) {
            return false;
        }

        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    public static final class AddUserInput {
        private final int userId;
        private final String firstName;
        private final String lastName;

        public AddUserInput(int userId, String firstName, String lastName) {
            this.userId = userId;
            this.firstName = firstName;
            this.lastName = lastName;
        }

        public int getUserId() {
            return userId;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }
    }

    public static final class EditUserInput {
        private final String firstName;
        private final String lastName;

        public EditUserInput(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }
    }
}
