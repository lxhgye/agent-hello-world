package cn.cgn.chat.service.cTools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.document.BlankDocumentException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

/**
 * Windows 操作工具，使用 Java NIO、ProcessBuilder 和 ProcessHandle 操作一个指定目录及普通程序。
 *
 * <p>当前类用于演示，不包含用户确认、程序白名单和返回数量限制，不能直接作为正式版本使用。</p>
 */
public final class WindowsOperationTool {

    private static final long MINIMUM_USER_PROCESS_ID = 5L;

    private final Path allowedDirectory;
    private final ConcurrentMap<Long, Process> startedProcesses = new ConcurrentHashMap<>();

    /**
     * 创建仅允许操作指定目录的 Windows 工具。
     *
     * @param allowedDirectory 唯一允许操作的目录
     */
    public WindowsOperationTool(Path allowedDirectory) {
        if (allowedDirectory == null) {
            throw new IllegalArgumentException("允许操作的目录不能为空");
        }
        try {
            Path realDirectory = allowedDirectory.toAbsolutePath().normalize().toRealPath();
            if (!Files.isDirectory(realDirectory)) {
                throw new IllegalArgumentException("允许操作的路径不是目录：" + realDirectory);
            }
            this.allowedDirectory = realDirectory;
        } catch (IOException exception) {
            throw new IllegalArgumentException("允许操作的目录不存在或无法访问：" + allowedDirectory, exception);
        }
    }

    /**
     * 列出指定目录的直接子项。
     *
     * @param directoryPath 目录绝对路径
     * @return 每行一个子项的类型、名称、大小和修改时间；目录为空时返回目录为空提示
     */
    @Tool("列出允许目录范围内指定目录的直接子项，不递归；返回每行一个子项的类型、名称、大小和修改时间，目录为空时返回提示")
    public String listDirectory(
            @P(name = "directoryPath", description = "允许目录范围内的目录绝对路径") String directoryPath) {
        Path directory = resolveExistingPath(directoryPath, Files::isDirectory, "目录不存在或不是目录");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            List<String> entries = new ArrayList<>();
            for (Path entry : stream) {
                entries.add(describePath(entry));
            }
            entries.sort(String.CASE_INSENSITIVE_ORDER);
            return entries.isEmpty() ? "目录为空：" + directory : String.join(System.lineSeparator(), entries);
        } catch (IOException exception) {
            throw new IllegalStateException("列出目录失败：" + directory, exception);
        }
    }

    /**
     * 按文件名通配符递归搜索普通文件。
     *
     * @param rootDirectoryPath 搜索根目录绝对路径
     * @param fileNameGlob 文件名 glob 表达式，例如 *.txt
     * @return 每行一个匹配文件的绝对路径；没有匹配项时返回未找到提示
     */
    @Tool("在允许目录范围内按文件名 glob 通配符递归搜索普通文件；返回每行一个匹配文件的绝对路径，没有匹配项时返回未找到提示")
    public String searchFiles(
            @P(name = "rootDirectoryPath", description = "允许目录范围内的搜索根目录绝对路径")
            String rootDirectoryPath,
            @P(name = "fileNameGlob", description = "仅匹配文件名的 glob 通配符，例如 *.log 或 report-?.txt")
            String fileNameGlob) {
        Path rootDirectory = resolveExistingPath(
                rootDirectoryPath, Files::isDirectory, "搜索根目录不存在或不是目录");
        if (fileNameGlob == null || fileNameGlob.isBlank()) {
            throw new IllegalArgumentException("文件名通配符不能为空");
        }
        try {
            var matcher = rootDirectory.getFileSystem().getPathMatcher("glob:" + fileNameGlob);
            try (var pathStream = Files.walk(rootDirectory)) {
                List<String> matches = pathStream
                        .filter(path -> !Files.isSymbolicLink(path))
                        .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .filter(path -> matcher.matches(path.getFileName()))
                        .map(Path::toAbsolutePath)
                        .map(Path::normalize)
                        .map(Path::toString)
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .toList();
                return matches.isEmpty() ? "未找到匹配文件" : String.join(System.lineSeparator(), matches);
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("搜索文件失败：" + rootDirectory, exception);
        }
    }

    /**
     * 读取 UTF-8 文本文件。
     *
     * @param filePath 文件绝对路径
     * @return 文件的完整 UTF-8 文本内容
     */
    @Tool("读取允许目录范围内的 UTF-8 文本文件；返回文件的完整文本内容")
    public String readTextFile(
            @P(name = "filePath", description = "允许目录范围内的文本文件绝对路径") String filePath) {
        Path file = resolveExistingPath(filePath, Files::isRegularFile, "文件不存在或不是普通文件");
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return content;
        } catch (IOException exception) {
            throw new IllegalStateException("读取文本文件失败：" + file, exception);
        }
    }

    /**
     * 使用 LangChain4j 的 Apache Tika 文档解析器提取常见文档文本。
     *
     * <p>解析器根据文件内容识别 PDF、Word、Excel 等格式。扫描件或纯图片 PDF 没有可选文字时，
     * 需要额外接入 OCR 能力，不能把图片内容当作普通文本读取。</p>
     *
     * @param filePath 允许目录范围内的 PDF、Word 或 Excel 文件绝对路径
     * @return 提取出的文本；没有可选文字时返回需要 OCR 的明确提示
     */
    @Tool("使用 LangChain4j Apache Tika 读取允许目录范围内的 PDF、Word、Excel 等文档并提取文本；扫描件或图片型 PDF 没有文字层时返回需要 OCR 的提示")
    public String readDocumentFile(
            @P(name = "filePath", description = "允许目录范围内的 PDF、Word 或 Excel 文件绝对路径") String filePath) {
        Path file = resolveExistingPath(filePath, Files::isRegularFile, "文档不存在或不是普通文件");
        try (var inputStream = Files.newInputStream(file)) {
            Document document = new ApacheTikaDocumentParser().parse(inputStream);
            String text = document.text() == null ? "" : document.text().trim();
            if (text.isEmpty()) {
                return "文档未提取到可选文本，可能是扫描件或图片型 PDF，需要 OCR。文件：" + file;
            }
            return text;
        } catch (BlankDocumentException exception) {
            return "文档未提取到可选文本，可能是扫描件或图片型 PDF，需要 OCR。文件：" + file;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("读取文档失败：" + file, exception);
        }
    }

    /**
     * 创建目录及缺失的父目录。
     *
     * @param directoryPath 待创建目录绝对路径
     * @return 创建完成提示和创建后的目录绝对路径
     */
    @Tool("在允许目录范围内创建目录及缺失的父目录；成功时返回创建后的目录绝对路径")
    public String createDirectory(
            @P(name = "directoryPath", description = "允许目录范围内待创建的目录绝对路径")
            String directoryPath) {
        Path directory = resolveWritablePath(directoryPath);
        try {
            Files.createDirectories(directory);
            if (!Files.isDirectory(directory)) {
                throw new IllegalStateException("目录创建后验证失败：" + directory);
            }
            return "目录创建完成：" + directory;
        } catch (IOException exception) {
            throw new IllegalStateException("创建目录失败：" + directory, exception);
        }
    }

    /**
     * 使用 UTF-8 编码写入文本文件。
     *
     * @param filePath 目标文件绝对路径
     * @param content 待写入内容
     * @param overwrite 文件存在时是否覆盖
     * @return 写入完成提示和目标文件绝对路径
     */
    @Tool("使用 UTF-8 写入允许目录范围内的文本文件；overwrite 为 false 时文件已存在将执行失败，为 true 时覆盖原内容；成功时返回目标文件绝对路径")
    public String writeTextFile(
            @P(name = "filePath", description = "允许目录范围内的目标文件绝对路径") String filePath,
            @P(name = "content", description = "需要完整写入文件的 UTF-8 文本内容") String content,
            @P(name = "overwrite", description = "文件已存在时是否清空原内容并覆盖；false 表示拒绝覆盖")
            boolean overwrite) {
        if (content == null) {
            throw new IllegalArgumentException("待写入内容不能为空对象");
        }
        Path file = resolveWritablePath(filePath);
        OpenOption[] options = overwrite
                ? new OpenOption[]{StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE}
                : new OpenOption[]{StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE};
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8, options);
            verifyRegularFile(file, "文本文件写入后验证失败");
            return "文本文件写入完成：" + file;
        } catch (IOException exception) {
            throw new IllegalStateException("写入文本文件失败：" + file, exception);
        }
    }

    /**
     * 使用 UTF-8 编码向文本文件追加内容。
     *
     * @param filePath 目标文件绝对路径
     * @param content 待追加内容
     * @return 追加完成提示和目标文件绝对路径
     */
    @Tool("使用 UTF-8 向允许目录范围内的文本文件末尾追加内容，文件不存在时自动创建；成功时返回目标文件绝对路径")
    public String appendTextFile(
            @P(name = "filePath", description = "允许目录范围内的目标文件绝对路径") String filePath,
            @P(name = "content", description = "需要追加到文件末尾的 UTF-8 文本内容，不会自动添加换行")
            String content) {
        if (content == null) {
            throw new IllegalArgumentException("待追加内容不能为空对象");
        }
        Path file = resolveWritablePath(filePath);
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            verifyRegularFile(file, "文本文件追加后验证失败");
            return "文本文件追加完成：" + file;
        } catch (IOException exception) {
            throw new IllegalStateException("追加文本文件失败：" + file, exception);
        }
    }

    /**
     * 复制单个普通文件。
     *
     * @param sourceFilePath 源文件绝对路径
     * @param targetFilePath 目标文件绝对路径
     * @param overwrite 目标存在时是否覆盖
     * @return 复制完成提示和目标文件绝对路径
     */
    @Tool("在允许目录范围内复制单个普通文件；overwrite 为 false 时目标已存在将执行失败，为 true 时覆盖目标；成功时返回目标文件绝对路径")
    public String copyFile(
            @P(name = "sourceFilePath", description = "允许目录范围内的源文件绝对路径") String sourceFilePath,
            @P(name = "targetFilePath", description = "允许目录范围内的目标文件绝对路径，包含目标文件名")
            String targetFilePath,
            @P(name = "overwrite", description = "目标文件已存在时是否覆盖；false 表示拒绝覆盖")
            boolean overwrite) {
        Path sourceFile = resolveExistingPath(sourceFilePath, Files::isRegularFile, "源文件不存在或不是普通文件");
        Path targetFile = resolveWritablePath(targetFilePath);
        try {
            if (overwrite) {
                Files.copy(sourceFile, targetFile,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            } else {
                Files.copy(sourceFile, targetFile, StandardCopyOption.COPY_ATTRIBUTES);
            }
            verifyRegularFile(targetFile, "文件复制后验证失败");
            return "文件复制完成：" + targetFile;
        } catch (IOException exception) {
            throw new IllegalStateException("复制文件失败：" + targetFile, exception);
        }
    }

    /**
     * 移动或重命名文件、目录。
     *
     * @param sourcePath 源路径绝对地址
     * @param targetPath 目标路径绝对地址
     * @param overwrite 目标存在时是否替换
     * @return 移动完成提示和目标绝对路径
     */
    @Tool("在允许目录范围内移动或重命名文件、目录；overwrite 为 false 时目标已存在将执行失败，为 true 时尝试替换目标；成功时返回目标绝对路径")
    public String movePath(
            @P(name = "sourcePath", description = "允许目录范围内的源文件或目录绝对路径") String sourcePath,
            @P(name = "targetPath", description = "允许目录范围内的目标绝对路径，重命名时应包含新名称")
            String targetPath,
            @P(name = "overwrite", description = "目标已存在时是否尝试替换；false 表示拒绝替换")
            boolean overwrite) {
        Path source = resolveExistingPath(sourcePath, Files::exists, "源路径不存在");
        Path target = resolveWritablePath(targetPath);
        rejectAllowedDirectoryMutation(source);
        rejectAllowedDirectoryMutation(target);
        try {
            if (overwrite) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target);
            }
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("路径移动后验证失败：" + target);
            }
            return "路径移动完成：" + target;
        } catch (IOException exception) {
            throw new IllegalStateException("移动路径失败：" + target, exception);
        }
    }

    /**
     * 删除单个普通文件，不支持目录及递归删除。
     *
     * @param filePath 待删除文件绝对路径
     * @return 删除完成提示和被删除文件的绝对路径
     */
    @Tool("删除允许目录范围内的单个普通文件，不支持目录、递归删除或符号链接；成功时返回被删除文件的绝对路径")
    public String deleteFile(
            @P(name = "filePath", description = "允许目录范围内待删除的普通文件绝对路径") String filePath) {
        Path file = resolveExistingPath(filePath, Files::isRegularFile, "待删除路径不存在或不是普通文件");
        try {
            Files.delete(file);
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("文件删除后验证失败：" + file);
            }
            return "普通文件删除完成：" + file;
        } catch (IOException exception) {
            throw new IllegalStateException("删除普通文件失败：" + file, exception);
        }
    }

    /**
     * 查询当前可见进程。
     *
     * @return 每行一个进程的 PID、名称、程序路径和启动时间
     */
    @Tool("查询当前可见进程；返回每行一个进程的 PID、名称、程序路径和启动时间，无法读取的字段显示为未知")
    public String listProcesses() {
        List<String> processes = ProcessHandle.allProcesses()
                .sorted(Comparator.comparingLong(ProcessHandle::pid))
                .map(this::describeProcess)
                .toList();
        return processes.isEmpty() ? "未查询到可见进程" : String.join(System.lineSeparator(), processes);
    }

    /**
     * 启动指定的本地可执行文件，并使用允许目录作为工作目录。
     *
     * @param executablePath 程序绝对路径
     * @param arguments 程序参数列表，每个元素作为一个独立参数
     * @return 启动完成提示、进程号 PID 和程序绝对路径
     */
    @Tool("启动指定的本地可执行文件，参数逐项传入且不经过 Shell，并使用允许目录作为工作目录；成功时返回 PID 和程序绝对路径")
    public String startProgram(
            @P(name = "executablePath", description = "需要启动的本地可执行文件绝对路径")
            String executablePath,
            @P(
                    name = "arguments",
                    description = "程序参数列表，每个元素是一个独立参数；没有参数时传空列表",
                    required = false,
                    defaultValue = "[]")
            List<String> arguments) {
        Path executable = resolveExecutable(executablePath);
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        if (arguments != null) {
            if (arguments.stream().anyMatch(argument -> argument == null)) {
                throw new IllegalArgumentException("程序参数不能包含 null");
            }
            command.addAll(arguments);
        }
        try {
            Process process = new ProcessBuilder(command)
                    .directory(allowedDirectory.toFile())
                    .start();
            startedProcesses.put(process.pid(), process);
            process.onExit().thenRun(() -> {
                startedProcesses.remove(process.pid());
            });
            return "程序启动完成，PID=" + process.pid() + "，程序=" + executable;
        } catch (IOException exception) {
            throw new IllegalStateException("启动程序失败：" + executable, exception);
        }
    }

    /**
     * 请求由当前工具启动的指定进程正常退出，不执行强制终止。
     *
     * @param processId 目标进程号
     * @return 包含 PID 的关闭状态，状态可能是已经退出、已提交退出请求或操作系统拒绝关闭
     */
    @Tool("按 PID 请求由当前工具启动且仍被跟踪的程序正常退出，不执行强制终止；返回已经退出、已提交退出请求或拒绝关闭状态，已提交不代表进程已经退出")
    public String stopProcess(
            @P(name = "processId", description = "startProgram 返回的目标进程号 PID") long processId) {
        verifyProcessId(processId);
        Process process = startedProcesses.get(processId);
        if (process == null) {
            throw new SecurityException("只能关闭当前工具启动且仍在运行的进程，PID=" + processId);
        }
        boolean accepted = process.toHandle().destroy();
        if (!accepted) {
            return "操作系统拒绝关闭进程，PID=" + processId;
        }
        if (!process.isAlive()) {
            startedProcesses.remove(processId);
            return "进程已经正常退出，PID=" + processId;
        }
        return "已经提交正常退出请求，PID=" + processId;
    }

    private Path resolveExistingPath(String rawPath, Predicate<Path> predicate, String errorMessage) {
        Path path = parsePath(rawPath);
        rejectSymbolicLink(path);
        try {
            Path realPath = path.toRealPath();
            verifyWithinAllowedDirectory(realPath);
            if (!predicate.test(realPath)) {
                throw new IllegalArgumentException(errorMessage + "：" + realPath);
            }
            return realPath;
        } catch (IOException exception) {
            throw new IllegalArgumentException(errorMessage + "：" + path, exception);
        }
    }

    private Path resolveWritablePath(String rawPath) {
        Path path = parsePath(rawPath);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            rejectSymbolicLink(path);
            try {
                Path realPath = path.toRealPath();
                verifyWithinAllowedDirectory(realPath);
                return realPath;
            } catch (IOException exception) {
                throw new IllegalArgumentException("解析可写目标路径失败：" + path, exception);
            }
        }

        Path existingAncestor = path.getParent();
        while (existingAncestor != null && !Files.exists(existingAncestor, LinkOption.NOFOLLOW_LINKS)) {
            existingAncestor = existingAncestor.getParent();
        }
        if (existingAncestor == null) {
            throw new IllegalArgumentException("目标路径没有可验证的现有父目录：" + path);
        }
        rejectSymbolicLink(existingAncestor);
        try {
            Path realAncestor = existingAncestor.toRealPath();
            verifyWithinAllowedDirectory(realAncestor);
            Path targetPath = realAncestor.resolve(existingAncestor.relativize(path)).normalize();
            verifyWithinAllowedDirectory(targetPath);
            return targetPath;
        } catch (IOException exception) {
            throw new IllegalArgumentException("验证可写目标路径失败：" + path, exception);
        }
    }

    private Path resolveExecutable(String rawPath) {
        Path executable = parsePath(rawPath);
        rejectSymbolicLink(executable);
        try {
            Path realExecutable = executable.toRealPath();
            if (!Files.isRegularFile(realExecutable)) {
                throw new IllegalArgumentException("程序路径不是普通文件：" + realExecutable);
            }
            return realExecutable;
        } catch (IOException exception) {
            throw new IllegalArgumentException("程序不存在或无法访问：" + executable, exception);
        }
    }

    private Path parsePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("路径不能为空");
        }
        return Path.of(rawPath).toAbsolutePath().normalize();
    }

    private void verifyWithinAllowedDirectory(Path path) {
        if (!path.startsWith(allowedDirectory)) {
            throw new SecurityException("路径不在允许目录内：" + path);
        }
    }

    private void rejectSymbolicLink(Path path) {
        if (Files.isSymbolicLink(path)) {
            throw new SecurityException("演示版不允许操作符号链接：" + path);
        }
    }

    private void rejectAllowedDirectoryMutation(Path path) {
        if (path.equals(allowedDirectory)) {
            throw new SecurityException("不允许移动或替换工具的允许目录本身：" + path);
        }
    }

    private void verifyRegularFile(Path file, String errorMessage) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException(errorMessage + "：" + file);
        }
    }

    private void verifyProcessId(long processId) {
        if (processId < MINIMUM_USER_PROCESS_ID) {
            throw new SecurityException("拒绝关闭系统关键进程，PID=" + processId);
        }
        if (processId == ProcessHandle.current().pid()) {
            throw new SecurityException("拒绝关闭 Windows 操作工具所在的 Java 进程");
        }
    }

    private String describePath(Path path) throws IOException {
        String type = Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) ? "目录" : "文件";
        long size = Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) ? Files.size(path) : 0L;
        Instant lastModifiedTime = Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant();
        return String.format("[%s] %s，大小=%d，修改时间=%s", type, path.getFileName(), size, lastModifiedTime);
    }

    private String describeProcess(ProcessHandle processHandle) {
        ProcessHandle.Info info = processHandle.info();
        String command = info.command().orElse("未知");
        String processName = extractFileName(command);
        String startTime = info.startInstant().map(Instant::toString).orElse("未知");
        return "PID=" + processHandle.pid() + "，名称=" + processName
                + "，路径=" + command + "，启动时间=" + startTime;
    }

    private String extractFileName(String command) {
        if ("未知".equals(command)) {
            return command;
        }
        try {
            Path fileName = Path.of(command).getFileName();
            return fileName == null ? command : fileName.toString();
        } catch (RuntimeException exception) {
            return command;
        }
    }
}
