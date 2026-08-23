package cn.cgn.chat.app;

import com.vladsch.flexmark.ext.gfm.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 使用开源 Flexmark 解析 Markdown，并将常用结构转换为 Windows 终端可读文本。
 * 解析能力由 Flexmark 提供，本类只负责终端样式和 HTML 标签适配。
 */
public final class MarkdownTerminalRenderer {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_BRIGHT_CYAN = "\u001B[96m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_MAGENTA = "\u001B[35m";
    private static final String ANSI_UNDERLINE = "\u001B[4m";
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final Parser parser;
    private final HtmlRenderer htmlRenderer;
    private final boolean ansiEnabled;
    private boolean streamingBold;
    private boolean streamingCode;
    private boolean streamingHeading;
    private boolean streamingLineStart = true;
    private char streamingPendingMarker;

    /**
     * 创建终端 Markdown 渲染器。
     */
    public MarkdownTerminalRenderer() {
        this(isAnsiSupported());
    }

    /**
     * 创建终端 Markdown 渲染器。
     *
     * @param ansiEnabled 是否输出 ANSI 样式控制码
     */
    public MarkdownTerminalRenderer(boolean ansiEnabled) {
        this.parser = Parser.builder()
                .extensions(List.of(TablesExtension.create()))
                .build();
        this.htmlRenderer = HtmlRenderer.builder().build();
        this.ansiEnabled = ansiEnabled;
    }

    /**
     * 将 Markdown 文本转换为终端可读文本。
     *
     * @param markdown Markdown 文本
     * @return 终端文本
     */
    public String render(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        String html = htmlRenderer.render(parser.parse(markdown));
        return toTerminalText(html);
    }

    /**
     * 将模型流式返回的片段立即转换为终端文本。
     *
     * <p>流式片段可能在 Markdown 标记中间截断，不能逐片段交给 Markdown 解析器，
     * 因此这里保留原文并仅增加可安全嵌套的终端颜色，保证用户能够即时看到模型输出。</p>
     *
     * @param chunk 模型返回的文本片段
     * @return 可立即写入终端的文本
     */
    public synchronized String renderStreaming(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }
        StringBuilder rendered = new StringBuilder(chunk.length());
        for (int index = 0; index < chunk.length();) {
            char current = chunk.charAt(index);

            if (streamingPendingMarker != 0) {
                if ((streamingPendingMarker == '*' && current == '*')
                        || (streamingPendingMarker == '`' && current == '`')) {
                    toggleStreamingStyle(rendered, streamingPendingMarker);
                    streamingPendingMarker = 0;
                    index++;
                    continue;
                }
                rendered.append(streamingPendingMarker);
                streamingPendingMarker = 0;
            }

            if (streamingLineStart && handleLinePrefix(chunk, index, rendered)) {
                int prefixLength = linePrefixLength(chunk, index);
                index += prefixLength;
                streamingLineStart = false;
                continue;
            }
            streamingLineStart = current == '\n' || current == '\r';

            if (current == '*' && index + 1 < chunk.length() && chunk.charAt(index + 1) == '*') {
                toggleStreamingStyle(rendered, '*');
                index += 2;
                continue;
            }
            if (current == '*' && index + 1 == chunk.length()) {
                streamingPendingMarker = '*';
                index++;
                continue;
            }
            if (current == '`') {
                toggleStreamingStyle(rendered, '`');
                index++;
                continue;
            }
            if (current == '\n' || current == '\r') {
                if (streamingHeading) {
                    rendered.append(style(ANSI_RESET));
                    streamingBold = false;
                    streamingHeading = false;
                }
                streamingLineStart = true;
            }
            rendered.append(current);
            index++;
        }
        return rendered.toString();
    }

    /**
     * 重置流式 Markdown 解析状态，开始渲染新的回答。
     */
    public synchronized void resetStreaming() {
        streamingBold = false;
        streamingCode = false;
        streamingHeading = false;
        streamingLineStart = true;
        streamingPendingMarker = 0;
    }

    /**
     * 结束流式 Markdown 渲染并清理未闭合样式。
     *
     * @return 需要写入终端的样式清理控制码
     */
    public synchronized String finishStreaming() {
        StringBuilder suffix = new StringBuilder();
        if (streamingPendingMarker != 0) {
            suffix.append(streamingPendingMarker);
        }
        if (streamingBold || streamingCode || streamingHeading) {
            suffix.append(style(ANSI_RESET));
        }
        resetStreaming();
        return suffix.toString();
    }

    private boolean handleLinePrefix(String chunk, int index, StringBuilder rendered) {
        int headingLength = headingPrefixLength(chunk, index);
        if (headingLength > 0) {
            rendered.append(style(ANSI_BRIGHT_CYAN + ANSI_BOLD));
            streamingBold = true;
            streamingHeading = true;
            return true;
        }
        if (chunk.startsWith("- ", index) || chunk.startsWith("* ", index)) {
            rendered.append(style(ANSI_GREEN)).append("• ").append(style(ANSI_RESET));
            return true;
        }
        return false;
    }

    private int linePrefixLength(String chunk, int index) {
        int headingLength = headingPrefixLength(chunk, index);
        if (headingLength > 0) {
            return headingLength;
        }
        if (chunk.startsWith("- ", index) || chunk.startsWith("* ", index)) {
            return 2;
        }
        return 0;
    }

    private int headingPrefixLength(String chunk, int index) {
        int cursor = index;
        while (cursor < chunk.length() && cursor - index < 6 && chunk.charAt(cursor) == '#') {
            cursor++;
        }
        return cursor > index && cursor < chunk.length() && chunk.charAt(cursor) == ' '
                ? cursor - index + 1
                : 0;
    }

    private void toggleStreamingStyle(StringBuilder rendered, char marker) {
        if (marker == '`') {
            streamingCode = !streamingCode;
            rendered.append(style(streamingCode ? ANSI_GREEN : ANSI_RESET));
            return;
        }
        streamingBold = !streamingBold;
        rendered.append(style(streamingBold ? ANSI_YELLOW + ANSI_BOLD : ANSI_RESET));
    }

    private String toTerminalText(String html) {
        String rendered = html
                .replaceAll("(?i)<h[1-6][^>]*>", style(ANSI_BRIGHT_CYAN + ANSI_BOLD))
                .replaceAll("(?i)</h[1-6]>", style(ANSI_RESET) + System.lineSeparator())
                .replaceAll("(?i)<strong>", style(ANSI_YELLOW + ANSI_BOLD))
                .replaceAll("(?i)</strong>", style(ANSI_RESET))
                .replaceAll("(?i)<em>", style(ANSI_MAGENTA))
                .replaceAll("(?i)</em>", style(ANSI_RESET))
                .replaceAll("(?i)<code[^>]*>", style(ANSI_GREEN))
                .replaceAll("(?i)</code>", style(ANSI_RESET))
                .replaceAll("(?i)<li>", style(ANSI_GREEN) + "• " + style(ANSI_RESET))
                .replaceAll("(?i)</li>", System.lineSeparator())
                .replaceAll("(?i)<tr[^>]*>", "")
                .replaceAll("(?i)</tr>", System.lineSeparator())
                .replaceAll("(?i)<t[dh][^>]*>", "  ")
                .replaceAll("(?i)</t[dh]>", "  ")
                .replaceAll("(?i)<br\s*/?>", System.lineSeparator())
                .replaceAll("(?i)</p>", System.lineSeparator() + System.lineSeparator())
                .replaceAll("(?i)<hr\s*/?>", System.lineSeparator() + "────────" + System.lineSeparator())
                .replaceAll("(?i)<a[^>]*href=\"([^\"]+)\"[^>]*>(.*?)</a>",
                        style(ANSI_BLUE + ANSI_UNDERLINE) + "$2" + style(ANSI_RESET) + " ($1)");
        rendered = decodeHtmlEntities(HTML_TAG_PATTERN.matcher(rendered).replaceAll(""));
        return rendered.replaceAll("(\r?\n){3,}", System.lineSeparator() + System.lineSeparator());
    }

    private String style(String ansiCode) {
        return ansiEnabled ? ansiCode : "";
    }

    private String decodeHtmlEntities(String value) {
        return value.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private static boolean isAnsiSupported() {
        return System.console() != null
                || System.getenv("WT_SESSION") != null
                || System.getenv("ANSICON") != null
                || System.getenv("TERM_PROGRAM") != null;
    }
}
