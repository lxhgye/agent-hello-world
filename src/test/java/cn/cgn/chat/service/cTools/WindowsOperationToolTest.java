package cn.cgn.chat.service.cTools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Windows 操作工具单元测试，验证文件生命周期、目录边界以及程序启动关闭能力。
 */
class WindowsOperationToolTest {

    private static final Pattern PROCESS_ID_PATTERN = Pattern.compile("PID=(\\d+)");

    @TempDir
    Path temporaryDirectory;

    @Test
    @DisplayName("应完成文本文件的创建、追加、复制、移动、读取和删除")
    void shouldCompleteTextFileLifecycle() {
        WindowsOperationTool tool = new WindowsOperationTool(temporaryDirectory);
        Path dataDirectory = temporaryDirectory.resolve("数据");
        Path sourceFile = dataDirectory.resolve("源文件.txt");
        Path copiedFile = dataDirectory.resolve("复制文件.txt");
        Path movedFile = dataDirectory.resolve("移动文件.txt");

        tool.createDirectory(dataDirectory.toString());
        tool.writeTextFile(sourceFile.toString(), "第一行", false);
        tool.appendTextFile(sourceFile.toString(), System.lineSeparator() + "第二行");
        tool.copyFile(sourceFile.toString(), copiedFile.toString(), false);
        tool.movePath(copiedFile.toString(), movedFile.toString(), false);

        String content = tool.readTextFile(movedFile.toString());
        assertTrue(content.contains("第一行"));
        assertTrue(content.contains("第二行"));
        assertFalse(Files.exists(copiedFile));
        assertTrue(Files.exists(movedFile));

        tool.deleteFile(movedFile.toString());
        assertFalse(Files.exists(movedFile));
    }

    @Test
    @DisplayName("应列出目录并按通配符递归搜索文件")
    void shouldListDirectoryAndSearchFiles() {
        WindowsOperationTool tool = new WindowsOperationTool(temporaryDirectory);
        Path nestedDirectory = temporaryDirectory.resolve("嵌套目录");
        Path textFile = nestedDirectory.resolve("演示.txt");
        Path logFile = nestedDirectory.resolve("演示.log");

        tool.createDirectory(nestedDirectory.toString());
        tool.writeTextFile(textFile.toString(), "文本", false);
        tool.writeTextFile(logFile.toString(), "日志", false);

        String directoryResult = tool.listDirectory(temporaryDirectory.toString());
        String searchResult = tool.searchFiles(temporaryDirectory.toString(), "*.txt");

        assertTrue(directoryResult.contains("嵌套目录"));
        assertTrue(searchResult.contains(textFile.toString()));
        assertFalse(searchResult.contains(logFile.toString()));
    }

    @Test
    @DisplayName("应提取带文字层 PDF 的文本")
    void shouldReadPdfText() throws IOException {
        WindowsOperationTool tool = new WindowsOperationTool(temporaryDirectory);
        Path pdfFile = temporaryDirectory.resolve("演示.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText("PDF text extraction demo");
                contentStream.endText();
            }
            document.save(pdfFile.toFile());
        }

        String content = tool.readDocumentFile(pdfFile.toString());
        assertTrue(content.contains("PDF text extraction demo"));
    }

    @Test
    @DisplayName("没有文字层的 PDF 应返回 OCR 提示而不是工具异常")
    void shouldReturnOcrHintForBlankPdf() throws IOException {
        WindowsOperationTool tool = new WindowsOperationTool(temporaryDirectory);
        Path pdfFile = temporaryDirectory.resolve("图片型文档.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdfFile.toFile());
        }

        String content = tool.readDocumentFile(pdfFile.toString());
        assertTrue(content.contains("需要 OCR"));
    }

    @Test
    @DisplayName("应拦截允许目录之外的路径")
    void shouldRejectPathOutsideAllowedDirectory() {
        WindowsOperationTool tool = new WindowsOperationTool(temporaryDirectory);
        Path outsidePath = temporaryDirectory.getParent().resolve("越界目录");

        assertThrows(SecurityException.class, () -> tool.createDirectory(outsidePath.toString()));
    }

    @Test
    @DisplayName("应启动并关闭由当前工具创建的测试进程")
    void shouldStartAndStopTrackedProcess() {
        Path pingExecutable = Path.of(System.getenv("SystemRoot"), "System32", "ping.exe");
        assumeTrue(Files.isRegularFile(pingExecutable), "当前环境不存在 Windows ping.exe");
        WindowsOperationTool tool = new WindowsOperationTool(temporaryDirectory);

        String startResult = tool.startProgram(
                pingExecutable.toString(), List.of("-t", "127.0.0.1"));
        long processId = extractProcessId(startResult);

        try {
            String stopResult = tool.stopProcess(processId);
            assertTrue(stopResult.contains("PID=" + processId));
        } finally {
            ProcessHandle.of(processId).filter(ProcessHandle::isAlive).ifPresent(ProcessHandle::destroyForcibly);
        }
    }

    private long extractProcessId(String startResult) {
        Matcher matcher = PROCESS_ID_PATTERN.matcher(startResult);
        if (!matcher.find()) {
            throw new IllegalStateException("启动结果中没有进程号：" + startResult);
        }
        return Long.parseLong(matcher.group(1));
    }
}
