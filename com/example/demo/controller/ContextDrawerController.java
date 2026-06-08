package com.example.demo.controller;

import com.example.demo.factory.SharedUiFactory;
import com.example.demo.model.RuleCardData;
import com.example.demo.model.User;
import com.example.demo.util.FormatUtils;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public class ContextDrawerController {
    private static final String MINI_GRAPH_TEMPLATE_PATH = "/com/example/demo/view/templates/miniFeedbackGraph.html";

    @FXML
    private VBox contextDrawer;
    @FXML
    private Label contextUseCaseLabel;
    @FXML
    private Label contextUsersLabel;
    @FXML
    private VBox activeRulesSummaryBox;
    @FXML
    private AnchorPane miniGraphAnchor;

    private WebView miniGraphWebView;
    private StackPane miniGraphLoadingOverlay;
    private JsonNode latestGraphData;

    private Supplier<String> selectedUseCaseSupplier = () -> null;
    private Supplier<User> selectedUserSupplier = () -> null;
    private BooleanSupplier allUsersSelectedSupplier = () -> false;
    private Supplier<List<RuleCardData>> rulesSupplier = List::of;
    private BiPredicate<User, RuleCardData> ruleAssignmentResolver = (user, rule) -> false;
    private Runnable graphRefreshCallback = () -> {};
    private Runnable mappingsRefreshCallback = () -> {};

    public void initializeDrawer() {
        setVisible(false);
        ensureMiniGraphWebView();
        showMiniGraphLoading(false);
        renderMiniGraphPlaceholder("Graph preview appears here.");
    }

    public void setVisible(boolean visible) {
        contextDrawer.setVisible(visible);
        contextDrawer.setManaged(visible);
    }

    public boolean isVisible() {
        return contextDrawer != null && contextDrawer.isVisible();
    }

    public void updateUseCaseLabel(String selectedUseCase) {
        if (contextUseCaseLabel != null) {
            contextUseCaseLabel.setText((selectedUseCase == null || selectedUseCase.isBlank()) ? "-" : selectedUseCase);
        }
    }

    public void updateUsersLabel(String text) {
        if (contextUsersLabel != null) {
            contextUsersLabel.setText((text == null || text.isBlank()) ? "-" : text);
        }
    }

    public void refreshRuleSummary() {
        activeRulesSummaryBox.getChildren().clear();

        String selectedUseCase = selectedUseCaseSupplier.get();
        if (selectedUseCase == null || selectedUseCase.isBlank()) {
            activeRulesSummaryBox.getChildren().add(new Label("No use case selected."));
            return;
        }

        User selectedUser = selectedUserSupplier.get();
        boolean allUsersSelected = allUsersSelectedSupplier.getAsBoolean();
        if (selectedUser == null && !allUsersSelected) {
            activeRulesSummaryBox.getChildren().add(new Label("No user selected."));
            return;
        }

        String selectedUseCaseKey = FormatUtils.normalizeUseCaseName(selectedUseCase);
        List<RuleCardData> filtered = rulesSupplier.get().stream()
            .filter(rule -> selectedUseCaseKey.equals(rule.useCaseKey))
            .filter(rule -> selectedUser == null || ruleAssignmentResolver.test(selectedUser, rule))
            .toList();

        if (filtered.isEmpty()) {
            activeRulesSummaryBox.getChildren().add(new Label(allUsersSelected
                ? "No mappings for this use case."
                : "No active mapping for this user."));
            return;
        }

        for (RuleCardData rule : filtered) {
            Label line = new Label(rule.rangeLabel + " | Pulses " + rule.pulseLabel + " | Intensity " + rule.intensityLabel);
            line.getStyleClass().add("drawer-rule-line");
            activeRulesSummaryBox.getChildren().add(line);
        }
    }

    public void renderMiniGraphFromData(JsonNode dataArray) {
        latestGraphData = dataArray;
        ensureMiniGraphWebView();

        if (dataArray == null || !dataArray.isArray() || dataArray.isEmpty()) {
            renderMiniGraphPlaceholder("No preview data available.");
            return;
        }

        String template = loadHtmlTemplate(MINI_GRAPH_TEMPLATE_PATH);
        if (template == null) {
            renderMiniGraphPlaceholder("Mini graph template file is missing.");
            return;
        }

        String escapedJson = dataArray.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        String html = template.replace("const data = DATA_PLACEHOLDER;", "const data = JSON.parse(\"" + escapedJson + "\");");

        miniGraphWebView.getEngine().loadContent(html);
    }

    public void renderLatestMiniGraph() {
        renderMiniGraphFromData(latestGraphData);
    }

    public void showMiniGraphLoading(boolean visible) {
        ensureMiniGraphWebView();
        miniGraphLoadingOverlay.setVisible(visible);
        miniGraphLoadingOverlay.setManaged(visible);
        if (visible) {
            miniGraphLoadingOverlay.toFront();
        }
    }

    public void setSelectedUseCaseSupplier(Supplier<String> selectedUseCaseSupplier) {
        this.selectedUseCaseSupplier = selectedUseCaseSupplier == null ? () -> null : selectedUseCaseSupplier;
    }

    public void setSelectedUserSupplier(Supplier<User> selectedUserSupplier) {
        this.selectedUserSupplier = selectedUserSupplier == null ? () -> null : selectedUserSupplier;
    }

    public void setAllUsersSelectedSupplier(BooleanSupplier allUsersSelectedSupplier) {
        this.allUsersSelectedSupplier = allUsersSelectedSupplier == null ? () -> false : allUsersSelectedSupplier;
    }

    public void setRulesSupplier(Supplier<List<RuleCardData>> rulesSupplier) {
        this.rulesSupplier = rulesSupplier == null ? List::of : rulesSupplier;
    }

    public void setRuleAssignmentResolver(BiPredicate<User, RuleCardData> resolver) {
        this.ruleAssignmentResolver = resolver == null ? (user, rule) -> false : resolver;
    }

    public void setGraphRefreshCallback(Runnable callback) {
        this.graphRefreshCallback = callback == null ? () -> {} : callback;
    }

    public void setMappingsRefreshCallback(Runnable callback) {
        this.mappingsRefreshCallback = callback == null ? () -> {} : callback;
    }

    @FXML
    private void onRefreshGraph() {
        graphRefreshCallback.run();
    }

    @FXML
    private void onRefreshMappings() {
        mappingsRefreshCallback.run();
    }

    private void renderMiniGraphPlaceholder(String message) {
        ensureMiniGraphWebView();
        miniGraphWebView.getEngine().loadContent(buildPlaceholderHtml(message));
    }

    private String buildPlaceholderHtml(String message) {
        return "<html><body style='font-family:Segoe UI, sans-serif;display:flex;align-items:center;justify-content:center;height:100%;margin:0;color:#4b5563;background:#ffffff;'>"
            + "<div style='text-align:center;padding:20px;'>" + escapeHtml(message) + "</div></body></html>";
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private void ensureMiniGraphWebView() {
        if (miniGraphWebView != null) {
            ensureMiniGraphLoadingOverlay();
            return;
        }
        miniGraphWebView = new WebView();
        miniGraphWebView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED || newState == Worker.State.FAILED || newState == Worker.State.CANCELLED) {
                showMiniGraphLoading(false);
            }
        });
        miniGraphWebView.setPrefHeight(180);
        AnchorPane.setTopAnchor(miniGraphWebView, 0.0);
        AnchorPane.setRightAnchor(miniGraphWebView, 0.0);
        AnchorPane.setBottomAnchor(miniGraphWebView, 0.0);
        AnchorPane.setLeftAnchor(miniGraphWebView, 0.0);
        miniGraphAnchor.getChildren().setAll(miniGraphWebView);
        ensureMiniGraphLoadingOverlay();
    }

    private void ensureMiniGraphLoadingOverlay() {
        if (miniGraphLoadingOverlay == null) {
            miniGraphLoadingOverlay = SharedUiFactory.createLoadingOverlay("Loading preview...");
            AnchorPane.setTopAnchor(miniGraphLoadingOverlay, 0.0);
            AnchorPane.setRightAnchor(miniGraphLoadingOverlay, 0.0);
            AnchorPane.setBottomAnchor(miniGraphLoadingOverlay, 0.0);
            AnchorPane.setLeftAnchor(miniGraphLoadingOverlay, 0.0);
        }

        if (!miniGraphAnchor.getChildren().contains(miniGraphLoadingOverlay)) {
            miniGraphAnchor.getChildren().add(miniGraphLoadingOverlay);
        }
    }

    private String loadHtmlTemplate(String resourcePath) {
        try {
            InputStream inputStream = this.getClass().getResourceAsStream(resourcePath);
            if (inputStream == null) {
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
            StringBuilder htmlContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                htmlContent.append(line).append("\n");
            }
            return htmlContent.toString();
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
