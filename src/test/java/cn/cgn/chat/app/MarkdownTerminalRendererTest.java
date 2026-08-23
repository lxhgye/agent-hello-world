package cn.cgn.chat.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Markdown 终端渲染测试，验证开源解析引擎转换后的基本控制台结构。
 */
class MarkdownTerminalRendererTest {

    @Test
    @DisplayName("应将标题、强调、列表和链接转换为终端文本")
    void shouldRenderCommonMarkdownStructures() {
        MarkdownTerminalRenderer renderer = new MarkdownTerminalRenderer(false);

        String result = renderer.render("# 标题\n\n**重点**\n\n- 第一项\n- 第二项\n\n[来源](https://example.com)");

        assertTrue(result.contains("标题"));
        assertTrue(result.contains("重点"));
        assertTrue(result.contains("• 第一项"));
        assertTrue(result.contains("https://example.com"));
        assertTrue(!result.contains("<h1>"));
    }

    @Test
    @DisplayName("流式 Markdown 片段跨响应边界时不应显示粗体标记")
    void shouldRenderStreamingBoldAcrossChunks() {
        MarkdownTerminalRenderer renderer = new MarkdownTerminalRenderer(false);

        String result = renderer.renderStreaming("**主要");
        result += renderer.renderStreaming("业务**");

        assertTrue(result.contains("主要业务"));
        assertTrue(!result.contains("**"));
    }

    @Test
    @DisplayName("终端 Markdown 样式应包含配色控制码")
    void shouldApplyTerminalColors() {
        MarkdownTerminalRenderer renderer = new MarkdownTerminalRenderer(true);

        String result = renderer.render("# 标题\n\n**重点**\n\n`代码`");

        assertTrue(result.contains("\u001B[96m"));
        assertTrue(result.contains("\u001B[33m"));
        assertTrue(result.contains("\u001B[32m"));
    }
}
