package cn.cgn.chat.app;

import cn.cgn.chat.service.gAIAgentic.PersonalAssistantAgent;

import java.io.Console;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.fusesource.jansi.AnsiConsole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 个人 AI 助手命令行启动程序，负责收集交互信息并持续处理通用任务。
 */
public final class WindowsAgentCliApplication {

    private static final String DIRECTORY_OPTION = "--directory";
    private static final String HELP_OPTION = "--help";
    private static final String EXIT_COMMAND = ":exit";
    private static final String QUIT_COMMAND = ":quit";
    private static final String HELP_COMMAND = ":help";
    private static final String DIRECTORY_COMMAND = ":directory";
    private static final String DIRECTORY_ENVIRONMENT_VARIABLE = "PERSONAL_ASSISTANT_DIRECTORY";
    private static final String PROMPT = "copilot> ";
    private static final String PLAN_STREAM_PREFIX = "\u0000P";
    private static final String ANSWER_START_PREFIX = "\u0000S";
    private static final String ANSWER_STREAM_PREFIX = "\u0000A";
    private static final String TOOL_STATUS_START_PREFIX = "\u0000W";
    private static final String TOOL_STATUS_END_PREFIX = "\u0000E";
    private static final String AGENT_STAGE_PREFIX = "\u0000G";
    private static final String ANSI_CLEAR_LINE = "\u001B[2K";
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_CYAN = "\u001B[96m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_BLUE = "\u001B[94m";
    private static final long STATUS_REFRESH_MILLISECONDS = 1_000L;
    private static final String NATIVE_ENCODING_PROPERTY = "native.encoding";
    private static final String VERSION = "0.1.0";
    private static final AtomicBoolean FINAL_ANSWER_STREAMED = new AtomicBoolean();
    private static final AtomicBoolean TOOL_STATUS_RUNNING = new AtomicBoolean();
    private static final AtomicReference<Thread> TOOL_STATUS_THREAD = new AtomicReference<>();
    /** 当前工具状态提示的开始时间；使用原子长整型避免空值拆箱异常。 */
    private static final AtomicLong TOOL_STATUS_START_NANOS = new AtomicLong();
    private static final Object OUTPUT_LOCK = new Object();
    private static final Logger LOGGER = LoggerFactory.getLogger(WindowsAgentCliApplication.class);
    private static volatile Charset consoleCharset = StandardCharsets.UTF_8;
    // CLI 通过 Jansi 负责适配终端；即使从 IDE 启动，也保留 ANSI 样式交给 Jansi 处理。
    private static final MarkdownTerminalRenderer MARKDOWN_RENDERER = new MarkdownTerminalRenderer(true);

    private WindowsAgentCliApplication() {
    }

    /**
     * 启动个人 AI 助手命令行交互程序。
     *
     * @param args 支持 --directory <目录> 和 --help
     */
    public static void main(String[] args) {
        configureConsoleOutput();
        AnsiConsole.systemInstall();
        try {
            printBanner();
            if (contains(args, HELP_OPTION)) {
                printUsage();
                return;
            }

            Path allowedDirectory = resolveDirectory(args);
            if (allowedDirectory == null) {
                printError("没有设置允许操作的目录，程序结束。");
                return;
            }

            printInfo("当前目录权限：" + allowedDirectory);
            printInfo("输入 :help 查看帮助，输入 :exit 或 :quit 退出。");

            runInteractiveLoop(allowedDirectory);
        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    private static void configureConsoleOutput() {
        consoleCharset = detectConsoleCharset();
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, consoleCharset));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, consoleCharset));
    }

    /**
     * 读取当前控制台实际使用的字符集，保证直接启动 EXE 时中文输出不乱码。
     *
     * <p>通过 start.bat 启动时，控制台通常已经是 UTF-8；直接双击 EXE 时，
     * {@link Console#charset()} 会返回 Windows 当前代码页对应的字符集。</p>
     */
    private static Charset detectConsoleCharset() {
        Console console = System.console();
        if (console != null) {
            return console.charset();
        }
        String nativeEncoding = System.getProperty(NATIVE_ENCODING_PROPERTY, StandardCharsets.UTF_8.name());
        try {
            return Charset.forName(nativeEncoding);
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("无法识别控制台字符集 {}，回退到 UTF-8。", nativeEncoding, exception);
            return StandardCharsets.UTF_8;
        }
    }

    private static void runInteractiveLoop(Path initialDirectory) {
        Path currentDirectory = initialDirectory;
        PersonalAssistantAgent agent = createAgent(currentDirectory);
        try (Scanner scanner = new Scanner(System.in, consoleCharset)) {
            while (true) {
                System.out.print(colorize(PROMPT, ANSI_BLUE));
                if (!scanner.hasNextLine()) {
                    System.out.println();
                    printInfo("输入流已结束，程序退出。");
                    return;
                }
                String instruction = scanner.nextLine().trim();
                if (instruction.isEmpty()) {
                    continue;
                }
                if (EXIT_COMMAND.equalsIgnoreCase(instruction) || QUIT_COMMAND.equalsIgnoreCase(instruction)) {
                    printInfo("感谢使用，程序退出。");
                    return;
                }
                if (HELP_COMMAND.equalsIgnoreCase(instruction)) {
                    printInteractiveHelp();
                    continue;
                }
                if (DIRECTORY_COMMAND.equalsIgnoreCase(instruction)) {
                    printInfo("当前允许目录：" + currentDirectory);
                    continue;
                }
                if (isDirectoryChangeCommand(instruction)) {
                    String requestedDirectory = instruction.substring(DIRECTORY_COMMAND.length()).trim();
                    if (requestedDirectory.isEmpty()) {
                        printError("用法：:directory <新的目录绝对路径>");
                        continue;
                    }
                    Path newDirectory = validateDirectory(requestedDirectory);
                    if (newDirectory != null) {
                        persistDirectory(newDirectory);
                        currentDirectory = newDirectory;
                        agent = createAgent(currentDirectory);
                        printInfo("允许目录已切换并保存：" + currentDirectory);
                    }
                    continue;
                }
                executeInstruction(agent, instruction);
            }
        }
    }

    private static PersonalAssistantAgent createAgent(Path directory) {
        return new PersonalAssistantAgent(directory, WindowsAgentCliApplication::printProgress);
    }

    private static boolean isDirectoryChangeCommand(String instruction) {
        return instruction.length() > DIRECTORY_COMMAND.length()
                && instruction.regionMatches(true, 0, DIRECTORY_COMMAND, 0, DIRECTORY_COMMAND.length())
                && Character.isWhitespace(instruction.charAt(DIRECTORY_COMMAND.length()));
    }

    private static void executeInstruction(PersonalAssistantAgent agent, String instruction) {
        FINAL_ANSWER_STREAMED.set(false);
        long startTime = System.nanoTime();
        try {
            String answer = agent.operate(instruction);
            long elapsedMilliseconds = (System.nanoTime() - startTime) / 1_000_000L;
            if (FINAL_ANSWER_STREAMED.get()) {
                System.out.print(MARKDOWN_RENDERER.finishStreaming());
            }
            System.out.println();
            if (!FINAL_ANSWER_STREAMED.get()) {
                System.out.print(MARKDOWN_RENDERER.render(answer));
                System.out.flush();
            }
            printInfo("本次处理完成，耗时约 " + elapsedMilliseconds + " 毫秒。");
        } catch (RuntimeException exception) {
            System.out.println();
            printError("本次处理失败：" + safeMessage(exception));
            printInfo("可以检查模型配置、网络连接、允许目录和工具返回的错误信息后重试。");
        } finally {
            stopToolStatus(null);
        }
    }

    private static Path resolveDirectory(String[] args) {
        String commandLineDirectory = readOption(args, DIRECTORY_OPTION);
        if (commandLineDirectory != null && !commandLineDirectory.isBlank()) {
            return validateDirectory(commandLineDirectory);
        }

        String configuredDirectory = System.getenv(DIRECTORY_ENVIRONMENT_VARIABLE);
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            Path directory = validateDirectory(configuredDirectory);
            if (directory != null) {
                return directory;
            }
            printInfo("已配置的允许目录无效，将重新询问目录。");
        }

        Console console = System.console();
        if (console == null) {
            printError("当前没有可交互控制台，请配置 PERSONAL_ASSISTANT_DIRECTORY 或使用 --directory <目录> 启动。");
            return null;
        }
        String input = console.readLine("请输入允许操作的目录绝对路径：");
        if (input == null || input.isBlank()) {
            return null;
        }
        Path directory = validateDirectory(input.trim());
        if (directory != null) {
            persistDirectory(directory);
        }
        return directory;
    }

    private static Path validateDirectory(String rawDirectory) {
        try {
            String normalizedInput = rawDirectory.trim();
            if (normalizedInput.length() >= 2 && normalizedInput.startsWith("\"")
                    && normalizedInput.endsWith("\"")) {
                normalizedInput = normalizedInput.substring(1, normalizedInput.length() - 1);
            }
            Path directory = Path.of(normalizedInput).toAbsolutePath().normalize().toRealPath();
            if (!Files.isDirectory(directory)) {
                throw new IllegalArgumentException("路径不是目录：" + directory);
            }
            return directory;
        } catch (Exception exception) {
            printError("允许操作目录无效：" + safeMessage(exception));
            return null;
        }
    }

    /**
     * 保存允许目录到当前 Windows 用户环境变量，供后续启动自动复用。
     *
     * @param directory 已通过目录边界校验的目录
     */
    private static void persistDirectory(Path directory) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;
        }
        try {
            Process process = new ProcessBuilder("setx", DIRECTORY_ENVIRONMENT_VARIABLE, directory.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(10L, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOGGER.warn("保存允许目录环境变量超时：{}", directory);
                return;
            }
            if (process.exitValue() != 0) {
                LOGGER.warn("保存允许目录环境变量失败，退出码：{}，目录：{}", process.exitValue(), directory);
                return;
            }
            LOGGER.info("允许目录已保存到当前用户环境变量：{}", directory);
        } catch (IOException exception) {
            LOGGER.warn("保存允许目录环境变量失败：{}", directory, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("等待保存允许目录环境变量时被中断：{}", directory, exception);
        }
    }

    private static String readOption(String[] args, String option) {
        for (int index = 0; index < args.length; index++) {
            if (option.equalsIgnoreCase(args[index])) {
                if (index + 1 >= args.length) {
                    printError("参数 " + option + " 缺少目录值。");
                    return null;
                }
                return args[index + 1];
            }
        }
        return null;
    }

    private static boolean contains(String[] args, String expected) {
        return Arrays.stream(args).anyMatch(expected::equalsIgnoreCase);
    }

    private static void printBanner() {
        System.out.println();
        System.out.println("========================================");
        System.out.println("      你的 AI 助手 " + VERSION);
        System.out.println("========================================");
    }

    private static void printUsage() {
        System.out.println("用法：java -jar agent-hello-world-1.0-SNAPSHOT-all.jar --directory <允许操作目录>");
        System.out.println("参数：");
        System.out.println("  --directory <目录>  设置唯一允许操作的目录");
        System.out.println("  --help              显示帮助");
        System.out.println("也可以配置用户环境变量 PERSONAL_ASSISTANT_DIRECTORY，启动时自动使用");
    }

    private static void printInteractiveHelp() {
        System.out.println("可输入自然语言指令，例如：");
        System.out.println("  根据实时资料规划任务并写入文件");
        System.out.println("  创建 notes\today.txt 并写入今天的工作记录");
        System.out.println("  搜索资料并生成带来源的报告");
        System.out.println("特殊命令：:help、:directory、:directory <新目录>、:exit、:quit");
    }

    private static void printInfo(String message) {
        System.out.println(message);
    }

    private static void printProgress(String message) {
        if ("任务计划：".equals(message)) {
            System.out.println();
            System.out.println(colorize("任务计划：", ANSI_CYAN));
            return;
        }
        if (message.startsWith(PLAN_STREAM_PREFIX)) {
            System.out.print(message.substring(PLAN_STREAM_PREFIX.length()));
            System.out.flush();
            return;
        }
        if (message.startsWith(AGENT_STAGE_PREFIX)) {
            String stage = message.substring(AGENT_STAGE_PREFIX.length());
            stopToolStatus(null);
            System.out.println(colorize(stage, ANSI_CYAN));
            startToolStatus(stage);
            return;
        }
        if (message.startsWith(ANSWER_START_PREFIX)) {
            stopToolStatus(null);
            MARKDOWN_RENDERER.resetStreaming();
            System.out.println();
            System.out.println(colorize("正在生成回答：", ANSI_CYAN));
            return;
        }
        if (message.startsWith(TOOL_STATUS_START_PREFIX)) {
            startToolStatus(message.substring(TOOL_STATUS_START_PREFIX.length()));
            return;
        }
        if (message.startsWith(TOOL_STATUS_END_PREFIX)) {
            stopToolStatus(message.substring(TOOL_STATUS_END_PREFIX.length()));
            return;
        }
        if (message.startsWith(ANSWER_STREAM_PREFIX)) {
            String chunk = message.substring(ANSWER_STREAM_PREFIX.length());
            System.out.print(MARKDOWN_RENDERER.renderStreaming(chunk));
            System.out.flush();
            if (System.lineSeparator().equals(chunk) || "\n".equals(chunk) || "\r\n".equals(chunk)) {
                FINAL_ANSWER_STREAMED.set(true);
            }
            return;
        }
        // 仅展示明确面向用户的状态；Agent、Planner 和工具名称写入后台日志。
    }

    /**
     * 启动单行工具等待提示，避免同步搜索期间用户误以为程序失去响应。
     *
     * @param message 当前工具阶段提示
     */
    private static void startToolStatus(String message) {
        stopToolStatus(null);
        TOOL_STATUS_RUNNING.set(true);
        long startNanos = System.nanoTime();
        TOOL_STATUS_START_NANOS.set(startNanos);
        printToolStatus(message, 0L);
        Thread statusThread = new Thread(() -> {
            while (TOOL_STATUS_RUNNING.get()) {
                try {
                    Thread.sleep(STATUS_REFRESH_MILLISECONDS);
                    if (!TOOL_STATUS_RUNNING.get()) {
                        return;
                    }
                    long elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000L;
                    printToolStatus(message, elapsedSeconds);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "personal-assistant-tool-status");
        statusThread.setDaemon(true);
        TOOL_STATUS_THREAD.set(statusThread);
        statusThread.start();
    }

    /**
     * 结束单行工具等待提示。
     *
     * @param message 完成提示；为空时不输出完成文本
     */
    private static void stopToolStatus(String message) {
        TOOL_STATUS_RUNNING.set(false);
        Thread statusThread = TOOL_STATUS_THREAD.getAndSet(null);
        long startNanos = TOOL_STATUS_START_NANOS.getAndSet(0L);
        if (statusThread != null) {
            statusThread.interrupt();
        }
        if (message != null && !message.isBlank()) {
            long elapsedSeconds = startNanos <= 0L
                    ? 0L
                    : (System.nanoTime() - startNanos) / 1_000_000_000L;
            synchronized (OUTPUT_LOCK) {
                String color = message.contains("失败") ? ANSI_RED : ANSI_GREEN;
                System.out.print("\r" + ANSI_CLEAR_LINE + colorize(
                        message + "，" + formatElapsed(elapsedSeconds), color) + "\n");
                System.out.flush();
            }
        } else if (statusThread != null || startNanos > 0L) {
            synchronized (OUTPUT_LOCK) {
                System.out.print("\r" + ANSI_CLEAR_LINE);
                System.out.flush();
            }
        }
    }

    private static void printToolStatus(String message, long elapsedSeconds) {
        synchronized (OUTPUT_LOCK) {
            System.out.print("\r" + ANSI_CLEAR_LINE + colorize(
                    message + " " + formatElapsed(elapsedSeconds), ANSI_YELLOW));
            System.out.flush();
        }
    }

    private static String formatElapsed(long totalSeconds) {
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes == 0L) {
            return "耗时 " + seconds + " 秒";
        }
        return "耗时 " + minutes + " 分钟 " + seconds + " 秒";
    }

    private static String colorize(String text, String color) {
        return color + text + ANSI_RESET;
    }

    private static void printError(String message) {
        System.err.println("错误：" + message);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }
}
