package com.example.demo.controller;

import com.example.demo.model.User;
import com.example.demo.service.ApiService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.scene.Node;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Controller for the left sidebar component
 * Handles use case and user sidebar management
 */
public class LeftSidebarController {
    private static final String SETTINGS_ICON_PATH = "/com/example/demo/images/settings.png";

    @FXML
    private ToggleButton useCasesNavButton;
    @FXML
    private ToggleButton UsersNavButton;
    @FXML
    private VBox useCasesSidebarPane;
    @FXML
    private VBox UsersSidebarPane;
    @FXML
    private ListView<String> useCaseListView;
    @FXML
    private ListView<User> UsersSidebarList;
    @FXML
    private Label UsersCountLabel;
    @FXML
    private Label selectedUserForAssignmentLabel;
    @FXML
    private Label currentUserUseCaseLabel;
    @FXML
    private ComboBox<String> userUseCaseComboBox;
    @FXML
    private Button assignUserUseCaseButton;

    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final ObservableList<String> useCases = FXCollections.observableArrayList();

    // Callbacks to parent controller
    private Consumer<String> onUseCaseSelected;
    private Consumer<User> onUserSelected;
    private Consumer<User> onEditUserRequested;
    private Runnable onAddUseCaseRequested;
    private Runnable onAssignUserUseCaseRequested;

    @FXML
    private void initialize() {
        setupSidebar();
    }

    private void setupSidebar() {
        useCasesNavButton.setOnAction(ignored -> setSidebarMode(true));
        UsersNavButton.setOnAction(ignored -> setSidebarMode(false));
        setSidebarMode(true);

        setupUsersSidebarListCellFactory();

        useCaseListView.setItems(useCases);
        useCaseListView.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null || newValue.isBlank()) {
                return;
            }
            if (onUseCaseSelected != null) {
                onUseCaseSelected.accept(newValue);
            }
        });

        if (userUseCaseComboBox != null) {
            userUseCaseComboBox.setItems(useCases);
            userUseCaseComboBox.valueProperty().addListener((obs, oldValue, newValue) -> updateAssignUserUseCaseButtonState());
        }
        updateAssignUserUseCaseButtonState();

        UsersSidebarList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                return;
            }
            updateUserAssignmentPanel(newValue);
            if (onUserSelected != null) {
                onUserSelected.accept(newValue);
            }
        });
    }

    private void setupUsersSidebarListCellFactory() {
        UsersSidebarList.setCellFactory(listView -> new ListCell<>() {
            private final Label userLabel = new Label();
            private final Region spacer = new Region();
            private final Button editButton = createEditUserButton();
            private final HBox content = new HBox(8, userLabel, spacer, editButton);

            {
                content.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                userLabel.getStyleClass().add("sidebar-user-row-label");
                editButton.setFocusTraversable(false);
            }

            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                userLabel.setText(formatUser(item));
                editButton.setOnAction(ignored -> {
                    if (onEditUserRequested != null) {
                        onEditUserRequested.accept(item);
                    }
                });
                setText(null);
                setGraphic(content);
            }
        });
    }

    @FXML
    private void onAddUseCaseRequested() {
        if (onAddUseCaseRequested != null) {
            onAddUseCaseRequested.run();
        }
    }

    @FXML
    private void onAssignUserUseCase() {
        if (onAssignUserUseCaseRequested != null) {
            onAssignUserUseCaseRequested.run();
        }
    }

    private void setSidebarMode(boolean useCasesMode) {
        useCasesNavButton.setSelected(useCasesMode);
        UsersNavButton.setSelected(!useCasesMode);

        useCasesSidebarPane.setVisible(useCasesMode);
        useCasesSidebarPane.setManaged(useCasesMode);

        UsersSidebarPane.setVisible(!useCasesMode);
        UsersSidebarPane.setManaged(!useCasesMode);
    }

    private void updateUserAssignmentPanel(User user) {
        if (user == null) {
            selectedUserForAssignmentLabel.setText("-");
            currentUserUseCaseLabel.setText("-");
            userUseCaseComboBox.setValue(null);
            updateAssignUserUseCaseButtonState();
            return;
        }

        String userDisplay = formatUser(user);
        selectedUserForAssignmentLabel.setText(userDisplay);

        String displayUsecase = user.getUsecaseName();
        currentUserUseCaseLabel.setText(displayUsecase == null || displayUsecase.isBlank() ? "-" : displayUsecase);

        if (displayUsecase != null && !displayUsecase.isBlank() && userUseCaseComboBox.getItems().contains(displayUsecase)) {
            userUseCaseComboBox.setValue(displayUsecase);
        } else if (!userUseCaseComboBox.getItems().isEmpty()) {
            userUseCaseComboBox.setValue(userUseCaseComboBox.getItems().get(0));
        }

        updateAssignUserUseCaseButtonState();
    }

    private void updateAssignUserUseCaseButtonState() {
        if (assignUserUseCaseButton == null) {
            return;
        }

        User selectedUser = UsersSidebarList.getSelectionModel().getSelectedItem();
        String selected = userUseCaseComboBox == null ? null : userUseCaseComboBox.getValue();

        if (selectedUser == null || selected == null || selected.isBlank()) {
            assignUserUseCaseButton.setDisable(true);
            return;
        }

        boolean unchanged = selected.equals(selectedUser.getUsecaseName());
        assignUserUseCaseButton.setDisable(unchanged);
    }

    private Button createEditUserButton() {
        Button button = new Button();
        button.getStyleClass().add("sidebar-user-settings-button");
        button.setTooltip(new Tooltip("Edit user name"));

        Image icon = null;
        try {
            icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream(SETTINGS_ICON_PATH)));
        } catch (Exception ignored) {
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

    private String formatUser(User user) {
        return user.getUserID() + " - " + user.getFName() + " " + user.getLName();
    }

    // Public API for parent controller
    public ObservableList<String> getUseCases() {
        return useCases;
    }

    public ObservableList<User> getUsers() {
        return users;
    }

    public void setOnUseCaseSelected(Consumer<String> callback) {
        this.onUseCaseSelected = callback;
    }

    public void setOnUserSelected(Consumer<User> callback) {
        this.onUserSelected = callback;
    }

    public void setOnEditUserRequested(Consumer<User> callback) {
        this.onEditUserRequested = callback;
    }

    public void setOnAddUseCaseRequested(Runnable callback) {
        this.onAddUseCaseRequested = callback;
    }

    public void setOnAssignUserUseCaseRequested(Runnable callback) {
        this.onAssignUserUseCaseRequested = callback;
    }

    public Label getUsersCountLabel() {
        return UsersCountLabel;
    }

    public Label getSelectedUserForAssignmentLabel() {
        return selectedUserForAssignmentLabel;
    }

    public Label getCurrentUserUseCaseLabel() {
        return currentUserUseCaseLabel;
    }

    public ListView<String> getUseCaseListView() {
        return useCaseListView;
    }

    public ListView<User> getUsersSidebarList() {
        return UsersSidebarList;
    }

    public ComboBox<String> getUserUseCaseComboBox() {
        return userUseCaseComboBox;
    }

    public void selectUser(User user) {
        UsersSidebarList.getSelectionModel().select(user);
    }

    public void selectUseCase(String useCase) {
        useCaseListView.getSelectionModel().select(useCase);
    }

    public User getSelectedUser() {
        return UsersSidebarList.getSelectionModel().getSelectedItem();
    }

    public String getSelectedUseCase() {
        return useCaseListView.getSelectionModel().getSelectedItem();
    }
}

