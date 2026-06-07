package com.example.demo.controller;

import com.example.demo.factory.MappingUiFactory;
import com.example.demo.model.Schedule;
import com.example.demo.model.ScheduleApiResponse;
import com.example.demo.model.User;
import com.example.demo.service.ApiService;
import com.example.demo.util.AlertUtils;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SchedulesTabController {
    private static final Pattern SCHEDULE_RELATIVE_NEXT_CHECK_PATTERN = Pattern.compile(
        "^\\s*(?:in\\s+)?(-?\\d+)\\s+(second|seconds|minute|minutes|hour|hours|day|days|week|weeks|month|months)\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    private static final List<DateTimeFormatter> SCHEDULE_NEXT_CHECK_FORMATTERS = List.of(
        DateTimeFormatter.ISO_INSTANT,
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_ZONED_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-M-d HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-M-d HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-M-d"),
        DateTimeFormatter.ofPattern("d/M/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("d/M/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("M/d/yyyy HH:mm"),
        DateTimeFormatter.ofPattern("M/d/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("d-M-yyyy HH:mm"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("d.M.yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("d.M.yyyy HH:mm"),
        DateTimeFormatter.ofPattern("d.M.yyyy")
    );

    @FXML
    private FlowPane schedulesFlowPane;
    @FXML
    private Label schedulesEmptyLabel;
    @FXML
    private TextField scheduleIdField;
    @FXML
    private TextField userIdField;
    @FXML
    private Spinner<Integer> intervalDaysSpinner;
    @FXML
    private ComboBox<String> measureTypeComboBox;
    @FXML
    private Spinner<Integer> triggerPercentageSpinner;
    @FXML
    private Label scheduleStatusLabel;

    private final ObservableList<Schedule> schedules = FXCollections.observableArrayList();
    private Supplier<ScheduleContext> contextSupplier = () -> new ScheduleContext("", null, false);
    private Function<Integer, String> userFormatter = userId -> userId == null || userId <= 0 ? "-" : String.valueOf(userId);
    private Schedule selectedScheduleForEdit;
    private boolean showingActiveSchedulesOnly;

    public record ScheduleContext(String useCaseName, User selectedUser, boolean allUsersSelected) {}

    @FXML
    private void initialize() {
        setupSchedulesTable();
    }

    public void setContextSupplier(Supplier<ScheduleContext> contextSupplier) {
        this.contextSupplier = contextSupplier == null
            ? () -> new ScheduleContext("", null, false)
            : contextSupplier;
    }

    public void setUserFormatter(Function<Integer, String> userFormatter) {
        this.userFormatter = userFormatter == null
            ? userId -> userId == null || userId <= 0 ? "-" : String.valueOf(userId)
            : userFormatter;
        renderScheduleCards();
    }

    public void refreshSchedulesForCurrentContext(boolean activeOnly) {
        showingActiveSchedulesOnly = activeOnly;
        ScheduleContext context = getContext();
        String uc = context.useCaseName();
        int userId = getScheduleListUserId(context);
        if (uc.isBlank() || (userId <= 0 && !context.allUsersSelected())) {
            schedules.clear();
            selectScheduleForEdit(null);
            renderScheduleCards();
            if (scheduleStatusLabel != null) {
                scheduleStatusLabel.setText("Select a user or All Users, and a use case, to view schedules.");
            }
            return;
        }

        CompletableFuture<ScheduleApiResponse> request = activeOnly
            ? ApiService.getInstance().listActiveSchedules(uc, userId)
            : ApiService.getInstance().listAllSchedules(uc, userId);

        request.thenAccept(resp -> Platform.runLater(() -> updateSchedulesTable(resp)));
    }

    public void clearSelection() {
        selectScheduleForEdit(null);
        if (userIdField != null) {
            userIdField.clear();
        }
    }

    public void syncSelectedUser(User user) {
        if (user != null && selectedScheduleForEdit == null && userIdField != null) {
            userIdField.setText(String.valueOf(user.getUserID()));
        }
    }

    private void setupSchedulesTable() {
        try {
            if (scheduleIdField != null) {
                scheduleIdField.setDisable(true);
                scheduleIdField.setEditable(false);
            }
            if (userIdField != null) {
                userIdField.setDisable(true);
                userIdField.setEditable(false);
            }

            if (measureTypeComboBox != null) {
                measureTypeComboBox.getItems().setAll("average", "median", "mode");
                measureTypeComboBox.setValue("average");
            }
            setupPositiveIntegerSpinner(intervalDaysSpinner, 1);
            setupTriggerPercentageSpinner();
            selectScheduleForEdit(null);
            renderScheduleCards();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupTriggerPercentageSpinner() {
        if (triggerPercentageSpinner == null) {
            return;
        }

        triggerPercentageSpinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(Integer.MIN_VALUE, Integer.MAX_VALUE, 1, 1)
        );
        triggerPercentageSpinner.setEditable(true);
        triggerPercentageSpinner.getEditor().setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            return newText.matches("-?\\d*") ? change : null;
        }));
    }

    private Instant getScheduleNextCheckSortInstant(Schedule schedule) {
        if (schedule == null) {
            return null;
        }

        return parseScheduleNextCheckInstant(schedule.getNextRunDate())
            .or(() -> parseScheduleNextCheckInstant(schedule.getNextCheck()))
            .orElse(null);
    }

    private Optional<Instant> parseScheduleNextCheckInstant(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Optional.empty();
        }

        String trimmed = value.trim();
        Optional<Instant> relativeInstant = parseRelativeScheduleNextCheckInstant(trimmed);
        if (relativeInstant.isPresent()) {
            return relativeInstant;
        }

        for (DateTimeFormatter formatter : SCHEDULE_NEXT_CHECK_FORMATTERS) {
            Optional<Instant> parsed = parseScheduleNextCheckInstant(trimmed, formatter);
            if (parsed.isPresent()) {
                return parsed;
            }
        }

        return Optional.empty();
    }

    private Optional<Instant> parseRelativeScheduleNextCheckInstant(String value) {
        Matcher matcher = SCHEDULE_RELATIVE_NEXT_CHECK_PATTERN.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        long amount;
        try {
            amount = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }

        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        long seconds;
        if (unit.startsWith("second")) {
            seconds = amount;
        } else if (unit.startsWith("minute")) {
            seconds = amount * 60L;
        } else if (unit.startsWith("hour")) {
            seconds = amount * 60L * 60L;
        } else if (unit.startsWith("day")) {
            seconds = amount * 24L * 60L * 60L;
        } else if (unit.startsWith("week")) {
            seconds = amount * 7L * 24L * 60L * 60L;
        } else {
            seconds = amount * 30L * 24L * 60L * 60L;
        }

        return Optional.of(Instant.EPOCH.plusSeconds(seconds));
    }

    private Optional<Instant> parseScheduleNextCheckInstant(String value, DateTimeFormatter formatter) {
        try {
            if (formatter == DateTimeFormatter.ISO_INSTANT) {
                return Optional.of(Instant.from(formatter.parse(value)));
            }
            if (formatter == DateTimeFormatter.ISO_OFFSET_DATE_TIME) {
                return Optional.of(OffsetDateTime.parse(value, formatter).toInstant());
            }
            if (formatter == DateTimeFormatter.ISO_ZONED_DATE_TIME) {
                return Optional.of(ZonedDateTime.parse(value, formatter).toInstant());
            }
            if (formatter == DateTimeFormatter.ISO_LOCAL_DATE_TIME || value.contains(":")) {
                return Optional.of(LocalDateTime.parse(value, formatter).atZone(ZoneId.systemDefault()).toInstant());
            }
            return Optional.of(LocalDate.parse(value, formatter).atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    private void populateScheduleFields(Schedule schedule) {
        if (schedule == null) {
            return;
        }

        if (scheduleIdField != null) {
            scheduleIdField.setText(String.valueOf(schedule.getScheduleId()));
        }
        if (userIdField != null) {
            userIdField.setText(String.valueOf(schedule.getUserId()));
        }
        if (intervalDaysSpinner != null && intervalDaysSpinner.getValueFactory() != null) {
            intervalDaysSpinner.getValueFactory().setValue(Math.max(1, schedule.getIntervalDays()));
        }
        if (measureTypeComboBox != null) {
            String measureType = schedule.getMeasureType() == null ? "" : schedule.getMeasureType().trim().toLowerCase(Locale.ROOT);
            measureTypeComboBox.setValue(List.of("average", "median", "mode").contains(measureType) ? measureType : "average");
        }
        if (triggerPercentageSpinner != null && triggerPercentageSpinner.getValueFactory() != null) {
            triggerPercentageSpinner.getValueFactory().setValue((int) Math.round(schedule.getTriggerPercentage()));
        }
    }

    private void updateSchedulesTable(ScheduleApiResponse resp) {
        if (resp == null) {
            return;
        }
        schedules.setAll(resp.getSchedules());
        if (selectedScheduleForEdit != null && schedules.stream().noneMatch(this::isSelectedSchedule)) {
            selectScheduleForEdit(null);
        }
        renderScheduleCards();
        updateScheduleStatus(resp);
    }

    private void updateScheduleStatus(ScheduleApiResponse resp) {
        if (resp == null || scheduleStatusLabel == null) {
            return;
        }
        scheduleStatusLabel.setText(resp.getReply() != null && !resp.getReply().isEmpty() ? resp.getReply() : (resp.isSuccess() ? "OK" : resp.getErrorMessage()));
    }

    private void renderScheduleCards() {
        if (schedulesFlowPane == null || schedulesEmptyLabel == null) {
            return;
        }

        schedulesFlowPane.getChildren().clear();
        if (schedules.isEmpty()) {
            schedulesEmptyLabel.setVisible(true);
            schedulesEmptyLabel.setManaged(true);
            schedulesEmptyLabel.setText("No schedules found.");
            return;
        }

        schedulesEmptyLabel.setVisible(false);
        schedulesEmptyLabel.setManaged(false);
        schedules.stream()
            .sorted(Comparator
                .comparing((Schedule schedule) -> getScheduleNextCheckSortInstant(schedule), Comparator.nullsLast(Instant::compareTo))
                .thenComparingInt(Schedule::getScheduleId))
            .map(this::createScheduleCard)
            .forEach(card -> schedulesFlowPane.getChildren().add(card));
    }

    private Node createScheduleCard(Schedule schedule) {
        VBox card = new VBox(8);
        card.getStyleClass().add("mapping-card");
        card.getStyleClass().add("schedule-card");
        if (!schedule.isActive()) {
            card.getStyleClass().add("schedule-card-inactive");
        }
        if (isSelectedSchedule(schedule)) {
            card.getStyleClass().add("mapping-card-selected");
        }

        Label title = new Label("Schedule #" + schedule.getScheduleId());
        title.getStyleClass().add("mapping-card-title");

        ToggleButton activeToggle = MappingUiFactory.createScheduleActiveToggle(schedule, this::onScheduleActiveToggleRequested);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox titleRow = new HBox(8, title, spacer, activeToggle);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        VBox details = new VBox(5,
            createScheduleDetailRow("User", formatScheduleUser(schedule.getUserId())),
            createScheduleDetailRow("Measure", normalizeScheduleDisplayValue(schedule.getMeasureType())),
            createScheduleDetailRow("Trigger", formatScheduleTrigger(schedule)),
            createScheduleDetailRow("Interval", Math.max(0, schedule.getIntervalDays()) + " days"),
            createScheduleDetailRow("Next check", formatScheduleNextCheck(schedule))
        );

        card.getChildren().addAll(titleRow, details);
        card.setOnMouseClicked(event -> {
            if (event.getTarget() instanceof ButtonBase) {
                return;
            }
            selectScheduleForEdit(schedule);
        });
        return card;
    }

    private Node createScheduleDetailRow(String labelText, String valueText) {
        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label(labelText + ":");
        label.getStyleClass().add("topbar-label");
        label.setMinWidth(72);

        Label value = new Label(valueText == null || valueText.isBlank() ? "-" : valueText);
        value.getStyleClass().add("topbar-value");
        value.setWrapText(true);
        row.getChildren().addAll(label, value);
        return row;
    }

    private String formatScheduleUser(int userId) {
        if (userId <= 0) {
            return "-";
        }
        return userFormatter.apply(userId);
    }

    private String normalizeScheduleDisplayValue(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String formatScheduleTrigger(Schedule schedule) {
        return String.valueOf((int) Math.round(schedule.getTriggerPercentage())) + "%";
    }

    private String formatScheduleNextCheck(Schedule schedule) {
        String nextCheck = schedule.getNextCheck();
        if (nextCheck == null || nextCheck.isBlank()) {
            nextCheck = schedule.getNextRunDate();
        }
        return nextCheck == null || nextCheck.isBlank() ? "-" : nextCheck;
    }

    private void selectScheduleForEdit(Schedule schedule) {
        selectedScheduleForEdit = schedule;
        if (schedule != null) {
            populateScheduleFields(schedule);
        } else {
            if (scheduleIdField != null) {
                scheduleIdField.clear();
            }
            ScheduleContext context = getContext();
            User currentUser = context.selectedUser();
            if (currentUser != null && userIdField != null) {
                userIdField.setText(String.valueOf(currentUser.getUserID()));
            }
        }
        renderScheduleCards();
    }

    private boolean isSelectedSchedule(Schedule schedule) {
        if (schedule == null || selectedScheduleForEdit == null) {
            return false;
        }
        return schedule.getScheduleId() > 0 && schedule.getScheduleId() == selectedScheduleForEdit.getScheduleId();
    }

    @FXML
    private void onListAllSchedules() {
        refreshSchedulesForCurrentContext(false);
    }

    @FXML
    private void onListActiveSchedules() {
        refreshSchedulesForCurrentContext(true);
    }

    @FXML
    private void onAddSchedule() {
        try {
            int userId = getSelectedScheduleUserId();
            int interval = getIntervalDaysValue();
            String measure = getSelectedScheduleMeasure();
            int trigger = getTriggerPercentageValue();
            String uc = requireSelectedUsecaseName();
            ApiService.getInstance().addSchedule(userId, interval, measure, trigger, uc)
                .thenAccept(resp -> Platform.runLater(() -> handleScheduleMutationResponse(resp)));
        } catch (Exception e) {
            showScheduleInputError(e);
        }
    }

    @FXML
    private void onSaveSchedule() {
        try {
            if (getCurrentScheduleIdForSave() > 0) {
                onChangeSchedule();
            } else {
                onAddSchedule();
            }
        } catch (Exception e) {
            showScheduleInputError(e);
        }
    }

    @FXML
    private void onChangeSchedule() {
        try {
            int schedId = getSelectedScheduleId();
            int userId = getSelectedScheduleUserId();
            int interval = getIntervalDaysValue();
            String measure = getSelectedScheduleMeasure();
            int trigger = getTriggerPercentageValue();
            String uc = requireSelectedUsecaseName();
            ApiService.getInstance().changeSchedule(schedId, userId, interval, measure, trigger, uc)
                .thenAccept(resp -> Platform.runLater(() -> handleScheduleMutationResponse(resp)));
        } catch (Exception e) {
            showScheduleInputError(e);
        }
    }

    @FXML
    private void onActivateSchedule() {
        try {
            int schedId = getSelectedScheduleId();
            String uc = requireSelectedUsecaseName();
            ApiService.getInstance().activateSchedule(schedId, uc)
                .thenAccept(resp -> Platform.runLater(() -> handleScheduleMutationResponse(resp)));
        } catch (Exception e) {
            showScheduleInputError(e);
        }
    }

    @FXML
    private void onDeactivateSchedule() {
        try {
            int schedId = getSelectedScheduleId();
            String uc = requireSelectedUsecaseName();
            ApiService.getInstance().deactivateSchedule(schedId, uc)
                .thenAccept(resp -> Platform.runLater(() -> handleScheduleMutationResponse(resp)));
        } catch (Exception e) {
            showScheduleInputError(e);
        }
    }

    private void onScheduleActiveToggleRequested(Schedule schedule, ToggleButton toggle) {
        if (schedule == null || toggle == null) {
            return;
        }

        boolean requestedActive = !schedule.isActive();
        String action = requestedActive ? "activate" : "deactivate";
        toggle.setSelected(schedule.isActive());
        toggle.setText(schedule.isActive() ? "Active" : "Inactive");

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(requestedActive ? "Activate Schedule" : "Deactivate Schedule");
        confirm.setHeaderText("Are you sure you want to " + action + " schedule #" + schedule.getScheduleId() + "?");
        confirm.setContentText(requestedActive
            ? "This will deactivate the current active schedule."
            : "This will deactivate the current schedule, and the user will have no active schedules.");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) {
            return;
        }

        selectScheduleForEdit(schedule);
        try {
            String uc = requireSelectedUsecaseName();
            CompletableFuture<ScheduleApiResponse> request = requestedActive
                ? ApiService.getInstance().activateSchedule(schedule.getScheduleId(), uc)
                : ApiService.getInstance().deactivateSchedule(schedule.getScheduleId(), uc);
            request.thenAccept(resp -> Platform.runLater(() -> handleScheduleMutationResponse(resp)));
        } catch (Exception e) {
            showScheduleInputError(e);
        }
    }

    private String getSelectedScheduleMeasure() {
        String measure = measureTypeComboBox == null ? "" : measureTypeComboBox.getValue();
        if (measure == null || measure.isBlank()) {
            throw new IllegalArgumentException("Please choose average, median, or mode.");
        }
        return measure.trim().toLowerCase(Locale.ROOT);
    }

    private void handleScheduleMutationResponse(ScheduleApiResponse resp) {
        if (resp == null) {
            return;
        }
        updateScheduleStatus(resp);
        if (resp.isSuccess()) {
            refreshSchedulesForCurrentContext(showingActiveSchedulesOnly);
        }
    }

    private int getTriggerPercentageValue() {
        if (triggerPercentageSpinner == null || triggerPercentageSpinner.getValueFactory() == null) {
            throw new IllegalStateException("Trigger percentage control is not available.");
        }

        String editorText = triggerPercentageSpinner.getEditor() == null
            ? ""
            : triggerPercentageSpinner.getEditor().getText().trim();
        if (!editorText.isEmpty()) {
            int typedValue = parseScheduleInteger(editorText, "Trigger percentage");
            if (typedValue == 0) {
                throw new IllegalArgumentException("Trigger percentage cannot be 0.");
            }
            triggerPercentageSpinner.getValueFactory().setValue(typedValue);
            return typedValue;
        }

        Integer value = triggerPercentageSpinner.getValue();
        if (value == null) {
            throw new IllegalArgumentException("Trigger percentage is required.");
        }
        if (value == 0) {
            throw new IllegalArgumentException("Trigger percentage cannot be 0.");
        }
        return value;
    }

    private int getIntervalDaysValue() {
        return getRequiredSpinnerValue(intervalDaysSpinner, "Interval days", 1);
    }

    private int getRequiredSpinnerValue(Spinner<Integer> spinner, String fieldName, int minValue) {
        if (spinner == null || spinner.getValueFactory() == null) {
            throw new IllegalStateException(fieldName + " control is not available.");
        }

        String editorText = spinner.getEditor() == null ? "" : spinner.getEditor().getText().trim();
        int value;
        if (!editorText.isEmpty()) {
            value = parseScheduleInteger(editorText, fieldName);
        } else {
            Integer spinnerValue = spinner.getValue();
            if (spinnerValue == null) {
                throw new IllegalArgumentException(fieldName + " is required.");
            }
            value = spinnerValue;
        }

        if (value < minValue) {
            throw new IllegalArgumentException(fieldName + " must be " + minValue + " or greater.");
        }

        spinner.getValueFactory().setValue(value);
        return value;
    }

    private void setupPositiveIntegerSpinner(Spinner<Integer> spinner, int initialValue) {
        if (spinner == null) {
            return;
        }

        spinner.setValueFactory(
            new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, Math.max(1, initialValue), 1)
        );
        spinner.setEditable(true);
        spinner.getEditor().setTextFormatter(new TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            return newText.matches("\\d*") ? change : null;
        }));
    }

    private void showScheduleInputError(Exception e) {
        String message = e == null || e.getMessage() == null || e.getMessage().isBlank()
            ? "Please make sure all schedule values are valid."
            : e.getMessage();
        AlertUtils.showErrorAlert("Input Error", message);
    }

    private int parseScheduleInteger(String text, String fieldName) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(fieldName + " must be a whole number.");
        }
    }

    private int getSelectedScheduleId() {
        int selectedId = getCurrentScheduleIdForSave();
        if (selectedId > 0) {
            return selectedId;
        }
        throw new IllegalArgumentException("Select a schedule first.");
    }

    private int getCurrentScheduleIdForSave() {
        if (selectedScheduleForEdit != null && selectedScheduleForEdit.getScheduleId() > 0) {
            return selectedScheduleForEdit.getScheduleId();
        }
        String text = scheduleIdField == null ? "" : scheduleIdField.getText().trim();
        if (text.isEmpty()) {
            return 0;
        }
        int scheduleId = parseScheduleInteger(text, "Schedule ID");
        if (scheduleId <= 0) {
            throw new IllegalArgumentException("Schedule ID must be greater than 0.");
        }
        return scheduleId;
    }

    private int getSelectedScheduleUserId() {
        User selected = getContext().selectedUser();
        if (selected != null && selected.getUserID() > 0) {
            return selected.getUserID();
        }
        String text = userIdField == null ? "" : userIdField.getText().trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Select a user first.");
        }
        int userId = parseScheduleInteger(text, "User ID");
        if (userId <= 0) {
            throw new IllegalArgumentException("User ID must be greater than 0.");
        }
        return userId;
    }

    private int getScheduleListUserId(ScheduleContext context) {
        User selected = context.selectedUser();
        if (selected != null && selected.getUserID() > 0) {
            return selected.getUserID();
        }
        return context.allUsersSelected() ? 0 : 0;
    }

    private String requireSelectedUsecaseName() {
        String usecaseName = getContext().useCaseName();
        if (usecaseName.isBlank()) {
            throw new IllegalArgumentException("Select a use case first.");
        }
        return usecaseName;
    }

    private ScheduleContext getContext() {
        ScheduleContext context = contextSupplier.get();
        if (context == null) {
            return new ScheduleContext("", null, false);
        }
        String useCaseName = context.useCaseName() == null ? "" : context.useCaseName().trim();
        return new ScheduleContext(useCaseName, context.selectedUser(), context.allUsersSelected());
    }
}
