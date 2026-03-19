package com.example.demo.controller.state;

import com.example.demo.model.User;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.time.LocalDate;

public class DashboardState {
    private static final DashboardState INSTANCE = new DashboardState();

    private final ObjectProperty<User> selectedParticipant = new SimpleObjectProperty<>();
    private final StringProperty selectedUseCase = new SimpleStringProperty();
    private final ObjectProperty<Integer> selectedUseCaseId = new SimpleObjectProperty<>(0);
    private final StringProperty selectedTimeRange = new SimpleStringProperty("Last 24 Hours");
    private final ObjectProperty<LocalDate> startDate = new SimpleObjectProperty<>(LocalDate.now().minusDays(7));
    private final ObjectProperty<LocalDate> endDate = new SimpleObjectProperty<>(LocalDate.now());

    private DashboardState() {
    }

    public static DashboardState getInstance() {
        return INSTANCE;
    }

    public ObjectProperty<User> selectedParticipantProperty() {
        return selectedParticipant;
    }

    public User getSelectedParticipant() {
        return selectedParticipant.get();
    }

    public void setSelectedParticipant(User selectedParticipant) {
        this.selectedParticipant.set(selectedParticipant);
    }

    public StringProperty selectedUseCaseProperty() {
        return selectedUseCase;
    }

    public String getSelectedUseCase() {
        return selectedUseCase.get();
    }

    public void setSelectedUseCase(String selectedUseCase) {
        this.selectedUseCase.set(selectedUseCase);
    }

    public ObjectProperty<Integer> selectedUseCaseIdProperty() {
        return selectedUseCaseId;
    }

    public int getSelectedUseCaseId() {
        Integer value = selectedUseCaseId.get();
        return value == null ? 0 : value;
    }

    public void setSelectedUseCaseId(int selectedUseCaseId) {
        this.selectedUseCaseId.set(selectedUseCaseId);
    }

    public StringProperty selectedTimeRangeProperty() {
        return selectedTimeRange;
    }

    public String getSelectedTimeRange() {
        return selectedTimeRange.get();
    }

    public void setSelectedTimeRange(String selectedTimeRange) {
        this.selectedTimeRange.set(selectedTimeRange);
    }

    public ObjectProperty<LocalDate> startDateProperty() {
        return startDate;
    }

    public LocalDate getStartDate() {
        return startDate.get();
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate.set(startDate);
    }

    public ObjectProperty<LocalDate> endDateProperty() {
        return endDate;
    }

    public LocalDate getEndDate() {
        return endDate.get();
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate.set(endDate);
    }
}

