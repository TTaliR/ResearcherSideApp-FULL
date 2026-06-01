package com.example.demo.factory;

import com.example.demo.service.ApiService;
import com.example.demo.util.MarkdownRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.paint.Color;
import javafx.scene.web.WebView;

public class ChatBubbleFactory {
    public static final double CHAT_BUBBLE_VIEWPORT_WIDTH = 580.0;
    public static final double CHAT_BUBBLE_MAX_WIDTH = 560.0;
    public static final double CHAT_BUBBLE_MIN_HEIGHT = 44.0;

    public static WebView createChatBubbleView(String text, boolean userMessage) {
        WebView webView = new WebView();
        webView.setContextMenuEnabled(true);
        webView.setFocusTraversable(false);
        webView.setMinWidth(CHAT_BUBBLE_VIEWPORT_WIDTH);
        webView.setPrefWidth(CHAT_BUBBLE_VIEWPORT_WIDTH);
        webView.setMaxWidth(CHAT_BUBBLE_VIEWPORT_WIDTH);
        webView.setMinHeight(CHAT_BUBBLE_MIN_HEIGHT);
        webView.setPrefHeight(CHAT_BUBBLE_MIN_HEIGHT);
        webView.setMaxHeight(CHAT_BUBBLE_MIN_HEIGHT);
        webView.setStyle("-fx-background-color: transparent;");
        webView.setPageFill(Color.TRANSPARENT);

        // Enable mouse wheel scrolling to pass through to parent ScrollPane
        webView.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, event -> {
            javafx.scene.input.ScrollEvent scrollEvent = (javafx.scene.input.ScrollEvent) event;
            if (scrollEvent.isControlDown() || scrollEvent.isShiftDown()) {
                event.consume();
            } else {
                event.consume();
                // Re-fire the event on the scroll pane to let it handle scrolling
                javafx.scene.Parent parent = webView.getParent();
                if (parent != null) {
                    parent.fireEvent(scrollEvent.copyFor(parent, parent));
                }
            }
        });

        webView.getEngine().loadContent(buildChatBubbleHtml(text, userMessage), "text/html");
        webView.getEngine().getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                Platform.runLater(() -> adjustChatBubbleHeight(webView, false));
            }
        });

        return webView;
    }

    private static void adjustChatBubbleHeight(WebView webView, boolean secondPass) {
        try {
            Object metrics = webView.getEngine().executeScript("(() => {"
                + "const bubble = document.querySelector('.bubble');"
                + "if (!bubble) return JSON.stringify({height: 0});"
                + "const height = bubble.getBoundingClientRect().height;"
                + "return JSON.stringify({height: Math.ceil(height)});"
                + "})()");

            if (metrics instanceof String metricJson && !metricJson.isBlank()) {
                JsonNode metricNode = ApiService.getInstance().getMapper().readTree(metricJson);
                double measuredHeight = metricNode.path("height").asDouble(0.0);

                double targetHeight = Math.max(CHAT_BUBBLE_MIN_HEIGHT, measuredHeight + 6);

                webView.setMinHeight(targetHeight);
                webView.setPrefHeight(targetHeight);
                webView.setMaxHeight(targetHeight);

                // Re-measure once after WebView paints; fonts and wrapping can settle one pulse later.
                if (!secondPass) {
                    Platform.runLater(() -> adjustChatBubbleHeight(webView, true));
                }
            }
        } catch (Exception ignored) {
            webView.setPrefHeight(120);
        }
    }

    public static String buildChatBubbleHtml(String markdown, boolean userMessage) {
        String renderedMarkdown = MarkdownRenderer.markdownToHtml(markdown);
        String bubbleBg = userMessage ? "#2563eb" : "#f8fafc";
        String bubbleText = userMessage ? "#ffffff" : "#111827";
        String bubbleBorder = userMessage ? "#1d4ed8" : "#d1d5db";
        String codeBg = userMessage ? "rgba(255,255,255,0.16)" : "#e5e7eb";
        String linkColor = userMessage ? "#dbeafe" : "#1d4ed8";
        String quoteColor = userMessage ? "#e5e7eb" : "#4b5563";
        String horizontalAlign = userMessage ? "flex-end" : "flex-start";

        return """
            <html>
              <head>
                <meta charset="UTF-8">
                <style>
                  html, body { margin: 0; padding: 0; background: transparent; overflow: hidden; }
                  body {
                    font-family: 'Segoe UI', 'Segoe UI Emoji', 'Segoe UI Symbol', sans-serif;
                    box-sizing: border-box;
                    display: flex;
                    justify-content: %s;
                    align-items: flex-start;
                    width: 580px;
                    min-height: 1px;
                  }
                  .bubble {
                    box-sizing: border-box;
                    display: inline-block;
                    width: fit-content;
                    min-width: 48px;
                    max-width: 560px;
                    padding: 10px 12px;
                    border-radius: 12px;
                    background: %s;
                    color: %s;
                    border: 1px solid %s;
                    white-space: normal;
                    overflow-wrap: anywhere;
                    word-break: break-word;
                    user-select: text;
                    -webkit-user-select: text;
                  }
                  .bubble p { margin: 0 0 8px 0; }
                  .bubble p:last-child { margin-bottom: 0; }
                  .bubble pre {
                    margin: 8px 0;
                    padding: 10px;
                    border-radius: 8px;
                    background: %s;
                    color: %s;
                    overflow-x: auto;
                    white-space: pre-wrap;
                    word-break: break-word;
                    font-family: Consolas, 'Courier New', 'Segoe UI Emoji', monospace;
                    font-size: 13px;
                  }
                  .bubble code {
                    font-family: Consolas, 'Courier New', 'Segoe UI Emoji', monospace;
                    background: %s;
                    padding: 1px 4px;
                    border-radius: 4px;
                  }
                  .bubble a { color: %s; }
                  .bubble ul, .bubble ol { margin: 0 0 8px 22px; padding: 0; }
                  .bubble li { margin: 0 0 4px 0; }
                  .bubble blockquote {
                    margin: 8px 0;
                    padding: 0 0 0 10px;
                    border-left: 3px solid rgba(156, 163, 175, 0.8);
                    color: %s;
                  }
                </style>
              </head>
              <body>
                <div class="bubble">%s</div>
              </body>
            </html>
            """.formatted(horizontalAlign, bubbleBg, bubbleText, bubbleBorder, codeBg, bubbleText, codeBg, linkColor, quoteColor, renderedMarkdown);
    }
}
