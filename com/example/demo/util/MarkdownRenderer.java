package com.example.demo.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class to render Markdown text to HTML.
 * <p>
 * Supports the following Markdown features:
 * <ul>
 *     <li>Headers (#, ##, ###)</li>
 *     <li>Bold (**text**)</li>
 *     <li>Italic (*text*)</li>
 *     <li>Code (`code`)</li>
 *     <li>Links ([text](url))</li>
 *     <li>Unordered lists (*, -, +)</li>
 *     <li>Ordered lists (1., 2., etc.)</li>
 *     <li>Code blocks (```)</li>
 *     <li>Blockquotes (> )</li>
 * </ul>
 */
public class MarkdownRenderer {

    private static final Pattern MARKDOWN_INLINE = Pattern.compile("\\[([^]]+)]\\(([^)]+)\\)|\\*\\*(.+?)\\*\\*|\\*(.+?)\\*|`([^`]+)`");

    /**
     * Converts a Markdown string to its HTML representation.
     *
     * @param markdown The Markdown string to convert.
     * @return The HTML representation of the Markdown string.
     */
    public static String markdownToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder html = new StringBuilder();
        StringBuilder paragraph = new StringBuilder();
        boolean inCodeBlock = false;
        boolean inUl = false;
        boolean inOl = false;

        String[] lines = normalized.split("\n", -1);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                if (paragraph.length() > 0) {
                    html.append("<p>").append(renderInlineMarkdown(paragraph.toString())).append("</p>");
                    paragraph.setLength(0);
                }
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                if (inCodeBlock) {
                    html.append("</code></pre>");
                    inCodeBlock = false;
                } else {
                    html.append("<pre><code>");
                    inCodeBlock = true;
                }
                continue;
            }

            if (inCodeBlock) {
                html.append(escapeHtml(line)).append("\n");
                continue;
            }

            if (trimmed.isEmpty()) {
                if (paragraph.length() > 0) {
                    html.append("<p>").append(renderInlineMarkdown(paragraph.toString())).append("</p>");
                    paragraph.setLength(0);
                }

                String nextNonEmpty = null;
                for (int nextIndex = lineIndex + 1; nextIndex < lines.length; nextIndex++) {
                    String candidate = lines[nextIndex].trim();
                    if (!candidate.isEmpty()) {
                        nextNonEmpty = candidate;
                        break;
                    }
                }

                boolean nextIsUl = nextNonEmpty != null && nextNonEmpty.matches("^(?:[-*+])\\s+.+");
                boolean nextIsOl = nextNonEmpty != null && nextNonEmpty.matches("^\\d+\\.\\s+.+");

                if (inUl && !nextIsUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl && !nextIsOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                continue;
            }

            if (trimmed.startsWith("# ")) {
                if (paragraph.length() > 0) {
                    html.append("<p>").append(renderInlineMarkdown(paragraph.toString())).append("</p>");
                    paragraph.setLength(0);
                }
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                html.append("<h1>").append(renderInlineMarkdown(trimmed.substring(2))).append("</h1>");
                continue;
            }

            if (trimmed.startsWith("## ")) {
                if (paragraph.length() > 0) {
                    html.append("<p>").append(renderInlineMarkdown(paragraph.toString())).append("</p>");
                    paragraph.setLength(0);
                }
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                html.append("<h2>").append(renderInlineMarkdown(trimmed.substring(3))).append("</h2>");
                continue;
            }
            if (trimmed.startsWith("### ")) {
                if (paragraph.length() > 0) {
                    html.append("<p>").append(renderInlineMarkdown(paragraph.toString())).append("</p>");
                    paragraph.setLength(0);
                }
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                html.append("<h3>").append(renderInlineMarkdown(trimmed.substring(4))).append("</h3>");
                continue;
            }

            if (trimmed.startsWith("> ")) {
                if (paragraph.length() > 0) {
                    html.append("<p>").append(renderInlineMarkdown(paragraph.toString())).append("</p>");
                    paragraph.setLength(0);
                }
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                html.append("<blockquote>").append(renderInlineMarkdown(trimmed.substring(2))).append("</blockquote>");
                continue;
            }

            if (trimmed.matches("^(?:[-*+])\\s+.+")) {
                if (paragraph.length() > 0) {
                    html.append("<p>").append(renderInlineMarkdown(paragraph.toString())).append("</p>");
                    paragraph.setLength(0);
                }
                if (inOl) {
                    html.append("</ol>");
                    inOl = false;
                }
                if (!inUl) {
                    html.append("<ul>");
                    inUl = true;
                }
                html.append("<li>").append(renderInlineMarkdown(trimmed.substring(1).trim())).append("</li>");
                continue;
            }

            if (trimmed.matches("^\\d+\\.\\s+.+")) {
                if (paragraph.length() > 0) {
                    html.append("<p>").append(renderInlineMarkdown(paragraph.toString())).append("</p>");
                    paragraph.setLength(0);
                }
                if (inUl) {
                    html.append("</ul>");
                    inUl = false;
                }
                if (!inOl) {
                    html.append("<ol>");
                    inOl = true;
                }
                int dotIndex = trimmed.indexOf('.');
                String explicitNumber = trimmed.substring(0, dotIndex).trim();
                String itemContent = trimmed.substring(dotIndex + 1).trim();
                html.append("<li value=\"")
                    .append(explicitNumber)
                    .append("\">")
                    .append(renderInlineMarkdown(itemContent))
                    .append("</li>");
                continue;
            }

            if (inUl) {
                html.append("</ul>");
                inUl = false;
            }
            if (inOl) {
                html.append("</ol>");
                inOl = false;
            }

            if (paragraph.length() > 0) {
                paragraph.append('\n');
            }
            paragraph.append(trimmed);
        }

        if (paragraph.length() > 0) {
            html.append("<p>").append(renderInlineMarkdown(paragraph.toString())).append("</p>");
        }
        if (inUl) {
            html.append("</ul>");
        }
        if (inOl) {
            html.append("</ol>");
        }
        if (inCodeBlock) {
            html.append("</code></pre>");
        }

        return html.toString();
    }

    /**
     * Renders inline Markdown elements to HTML.
     *
     * @param text The text to render.
     * @return The HTML representation of the inline Markdown.
     */
    private static String renderInlineMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder html = new StringBuilder();

        String[] lines = text.split("\\n", -1);
        for (int lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex];
            Matcher matcher = MARKDOWN_INLINE.matcher(line);
            int lastIndex = 0;

            while (matcher.find()) {
                html.append(escapeHtml(line.substring(lastIndex, matcher.start())));

                if (matcher.group(1) != null && matcher.group(2) != null) {
                    String label = escapeHtml(matcher.group(1));
                    String href = sanitizeHref(matcher.group(2));
                    if (href.isBlank()) {
                        html.append(escapeHtml(matcher.group(0)));
                    } else {
                        html.append("<a href=\"")
                            .append(escapeHtml(href))
                            .append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                            .append(label)
                            .append("</a>");
                    }
                } else if (matcher.group(3) != null) {
                    html.append("<strong>").append(escapeHtml(matcher.group(3))).append("</strong>");
                } else if (matcher.group(4) != null) {
                    html.append("<em>").append(escapeHtml(matcher.group(4))).append("</em>");
                } else if (matcher.group(5) != null) {
                    html.append("<code>").append(escapeHtml(matcher.group(5))).append("</code>");
                }

                lastIndex = matcher.end();
            }

            html.append(escapeHtml(line.substring(lastIndex)));
            if (lineIndex < lines.length - 1) {
                html.append("<br>");
            }
        }
        return html.toString();
    }

    /**
     * Sanitizes a URL to prevent XSS attacks.
     *
     * @param href The URL to sanitize.
     * @return The sanitized URL, or an empty string if the URL is invalid.
     */
    private static String sanitizeHref(String href) {
        if (href == null) {
            return "";
        }

        String trimmed = href.trim();
        if (trimmed.isEmpty()) {
            return "";
        }

        try {
            java.net.URI uri = java.net.URI.create(trimmed);
            String scheme = uri.getScheme();
            if (scheme == null || scheme.isBlank()) {
                return trimmed;
            }
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme) || "mailto".equalsIgnoreCase(scheme)) {
                return trimmed;
            }
        } catch (Exception ignored) {
            return "";
        }

        return "";
    }

    /**
     * Escapes HTML special characters in a string.
     *
     * @param text The string to escape.
     * @return The escaped string.
     */
    private static String escapeHtml(String text) {
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
}