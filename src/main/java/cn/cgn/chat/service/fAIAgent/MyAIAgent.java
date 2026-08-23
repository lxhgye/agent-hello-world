package cn.cgn.chat.service.fAIAgent;

import cn.cgn.chat.service.cTools.WindowsOperationTool;
import dev.langchain4j.agentic.Agent;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.service.V;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static cn.cgn.chat.config.ModelConfig.model;

/**
 * 个人 Windows Agentic 工作流，通过执行 Agent 和检查 Agent 循环完成用户指令。
 */
public final class MyAIAgent {

    private static final String USER_INSTRUCTION_KEY = "userInstruction";
    private static final String EXECUTION_RESULT_KEY = "executionResult";
    private static final String EXECUTION_AGENT_SYSTEM_MESSAGE_TEMPLATE = """
            你是 Windows 操作执行 Agent，负责根据用户指令调用 WindowsOperationTool 完成实际操作。

            当前用户设置的唯一允许文件操作目录是：%s

            必须严格遵守以下规则：
            1. 所有文件和目录操作只能发生在上述允许目录及其子目录中，禁止尝试访问或修改目录外路径。
            2. 用户提供相对路径时，将其解释为相对于允许目录的路径，并在调用工具前转换为绝对路径。
            3. 只能通过 WindowsOperationTool 执行实际操作，禁止虚构操作结果。
            4. 覆盖、移动、删除文件或关闭程序时可以直接执行，不需要再次询问用户确认。
            5. 用户要求创建文件并写入内容时，按需要依次创建目录并写入文件。
            6. 启动程序时参数必须逐项传递，禁止把程序路径和参数拼接成 Shell 命令。
            7. 启动程序成功后保留工具返回的 PID；关闭程序时使用该 PID。
            8. 如果上一轮检查结果包含改进意见，应根据意见修正执行方案，但不得绕过允许目录限制。
            9. 工具失败时如实保留错误信息，不要无意义地重复相同操作。
            10. 返回本轮执行步骤、工具真实结果、完成情况和仍存在的问题，供检查 Agent 判断。
            """;
    private static final String EXECUTION_AGENT_USER_MESSAGE = """
            用户原始指令：
            {{userInstruction}}
            """;

    private final Path allowedDirectory;

    public final WindowsExecutionAgent windowsExecutionAgent;


    /**
     * 创建个人 Windows Agentic 工作流。
     *
     * @param path 用户设置的唯一允许文件操作目录
     */
    public MyAIAgent(String path) {
        Path allowedDirectory = Path.of(path);
        this.allowedDirectory = resolveAllowedDirectory(allowedDirectory);
        WindowsOperationTool windowsOperationTool = new WindowsOperationTool(this.allowedDirectory);
        this.windowsExecutionAgent = AgenticServices.agentBuilder(WindowsExecutionAgent.class)
                .chatModel(model)
                .tools(windowsOperationTool)
                .userMessage(EXECUTION_AGENT_USER_MESSAGE)
                .systemMessage(EXECUTION_AGENT_SYSTEM_MESSAGE_TEMPLATE.formatted(path))
                .build();
    }


    private Path resolveAllowedDirectory(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("允许操作的目录不能为空");
        }
        try {
            Path realDirectory = directory.toAbsolutePath().normalize().toRealPath();
            if (!Files.isDirectory(realDirectory)) {
                throw new IllegalArgumentException("允许操作的路径不是目录：" + realDirectory);
            }
            return realDirectory;
        } catch (IOException exception) {
            throw new IllegalArgumentException("允许操作的目录不存在或无法访问：" + directory, exception);
        }
    }


    /**
     * Agent1：负责调用 Windows 工具执行用户指令，并根据上一轮意见修正执行方案。
     */
    public interface WindowsExecutionAgent {

        /**
         * 执行本轮 Windows 操作。
         *
         * @param userInstruction 用户原始指令
         * @return 本轮执行过程和工具真实结果
         */
        @Agent(
                name = "windows-execution-agent",
                description = "调用 WindowsOperationTool 执行用户操作",
                outputKey = EXECUTION_RESULT_KEY)
        String execute(
                @V(USER_INSTRUCTION_KEY) String userInstruction);
    }


    static void main() {
        String workPath = "D:\\may\\SZH\\信息管理\\知识文档\\";
        MyAIAgent myAIAgentic = new MyAIAgent(workPath);
        String answer = myAIAgentic.windowsExecutionAgent.execute("" +
                "帮我统计D:\\may\\SZH\\信息管理\\知识文档\\法规体系\\3部门规章和规范性文件\\ 有多少个文件，" +
                "各种类型文件各是多少个把结果写入到一个文件。");
        System.out.println(answer);

    }
}
