package com.example.demo.controller;

import com.example.demo.controller.state.DashboardState;
import com.example.demo.model.User;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;

import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Controller for the top bar component
 * Handles user selection, time range, and date filtering
 */
public class TopBarController {
    @FXML
    private Label selectedUseCaseLabel;
    @FXML
    private ComboBox<User> UsersComboBox;
    @FXML
    private ComboBox<String> timeRangeComboBox;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;

    private final DashboardState state = DashboardState.getInstance();
    private final ObservableList<User> users = FXCollections.observableArrayList();

    // Callbacks to parent controller
    private Consumer<User> onUserSelected;
    private Consumer<String> onTimeRangeChanged;
    private Consumer<LocalDate> onStartDateChanged;
    private Consumer<LocalDate> onEndDateChanged;
    private Runnable onGraphUpdateRequested;

    @FXML
    private void initialize() {
        setupTopbar();
    }

    private void setupTopbar() {
        UsersComboBox.setItems(users);
        UsersComboBox.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatUser(item));
            }
        });
        UsersComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select Users" : formatUser(item));
            }
        });

        UsersComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue == null) {
                state.setSelectedUsers(null);
                if (onUserSelected != null) {
                    onUserSelected.accept(null);
                }
                if (onGraphUpdateRequested != null) {
                    onGraphUpdateRequested.run();
                }
                return;
            }
            state.setSelectedUsers(newValue);
            if (onUserSelected != null) {
                onUserSelected.accept(newValue);
            }
            if (onGraphUpdateRequested != null) {
                onGraphUpdateRequested.run();
            }
        });

        timeRangeComboBox.getItems().setAll("Last 24 Hours", "Last 7 Days", "Last 30 Days", "All Time", "Custom Date Range");
        timeRangeComboBox.setValue("Last 24 Hours");
        timeRangeComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            state.setSelectedTimeRange(newValue);
            updateDatePickerVisibility();
            if (onTimeRangeChanged != null) {
                onTimeRangeChanged.accept(newValue);
            }
            if (onGraphUpdateRequested != null) {
                onGraphUpdateRequested.run();
            }
        });

        startDatePicker.setValue(LocalDate.now().minusDays(7));
        endDatePicker.setValue(LocalDate.now());
        state.setStartDate(startDatePicker.getValue());
        state.setEndDate(endDatePicker.getValue());

        startDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> {
            state.setStartDate(newValue);
            if (onStartDateChanged != null) {
                onStartDateChanged.accept(newValue);
            }
            if ("Custom Date Range".equals(state.getSelectedTimeRange())) {
                if (onGraphUpdateRequested != null) {
                    onGraphUpdateRequested.run();
                }
            }
        });

        endDatePicker.valueProperty().addListener((obs, oldValue, newValue) -> {
            state.setEndDate(newValue);
            if (onEndDateChanged != null) {
                onEndDateChanged.accept(newValue);
            }
            if ("Custom Date Range".equals(state.getSelectedTimeRange())) {
                if (onGraphUpdateRequested != null) {
                    onGraphUpdateRequested.run();
                }
            }
        });

        updateDatePickerVisibility();
    }

    private void updateDatePickerVisibility() {
        boolean custom = "Custom Date Range".equals(timeRangeComboBox.getValue());
        startDatePicker.setVisible(custom);
        startDatePicker.setManaged(custom);
        endDatePicker.setVisible(custom);
        endDatePicker.setManaged(custom);
    }

    private String formatUser(User user) {
        String name = (user.getFName() + " " + user.getLName()).trim();
        return name.isEmpty() ? String.valueOf(user.getUserID()) : user.getUserID() + " - " + name;
    }

    // Public API for parent controller
    public ObservableList<User> getUsers() {
        return users;
    }

    public void setOnUserSelected(Consumer<User> callback) {
        this.onUserSelected = callback;
    }

    public void setOnTimeRangeChanged(Consumer<String> callback) {
        this.onTimeRangeChanged = callback;
    }

    public void setOnStartDateChanged(Consumer<LocalDate> callback) {
        this.onStartDateChanged = callback;
    }

    public void setOnEndDateChanged(Consumer<LocalDate> callback) {
        this.onEndDateChanged = callback;
    }

    public void setOnGraphUpdateRequested(Runnable callback) {
        this.onGraphUpdateRequested = callback;
    }

    public Label getSelectedUseCaseLabel() {
        return selectedUseCaseLabel;
    }

    public ComboBox<User> getUsersComboBox() {
        return UsersComboBox;
    }

    public ComboBox<String> getTimeRangeComboBox() {
        return timeRangeComboBox;
    }

    public DatePicker getStartDatePicker() {
        return startDatePicker;
    }

    public DatePicker getEndDatePicker() {
        return endDatePicker;
    }

    public User getSelectedUser() {
        return UsersComboBox.getValue();
    }

    public void setSelectedUser(User user) {
        UsersComboBox.setValue(user);
    }

    public String getSelectedTimeRange() {
        return timeRangeComboBox.getValue();
    }

    public LocalDate getStartDate() {
        return startDatePicker.getValue();
    }

    public LocalDate getEndDate() {
        return endDatePicker.getValue();
    }
}

