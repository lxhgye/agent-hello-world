package cn.cgn.chat.app;

/**
 * 通用个人助手命令行正式入口。
 * 具体交互实现暂时复用兼容类，后续可在不影响 Agent 能力层的情况下独立演进。
 */
public final class PersonalAssistantCliApplication {

    private PersonalAssistantCliApplication() {
    }

    /**
     * 启动个人助手命令行。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        WindowsAgentCliApplication.main(args);
    }
}
