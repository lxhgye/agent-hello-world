# Windows Agent 命令行运行说明

## 构建

在项目根目录执行：

```powershell
mvn -q package
```

构建产物：

```text
target/agent-hello-world-1.0-SNAPSHOT-all.jar
```

这是包含运行依赖的可执行 Jar，不需要额外设置 Maven classpath。

## 启动

首次使用可以运行初始化脚本，配置当前用户的两个 API Key 和允许操作目录：

```powershell
powershell -ExecutionPolicy Bypass -File .\init-personal-assistant.ps1
```

脚本会配置以下用户环境变量，不会把 API Key 写入项目：

```text
DEEPSEEK_API_KEY
SEARCH_API_KEY
PERSONAL_ASSISTANT_DIRECTORY
```

关闭当前终端并重新打开后，直接运行：

```bat
start.bat
```

也可以手动指定目录启动：

```powershell
java '-Dfile.encoding=UTF-8' -jar target\agent-hello-world-1.0-SNAPSHOT-all.jar --directory "D:\AgentWorkspace"
```

命令行和便携版程序都会优先读取 `PERSONAL_ASSISTANT_DIRECTORY`；未配置时由程序交互询问目录。交互输入成功后，程序会将目录保存到当前 Windows 用户环境变量，后续启动无需重复输入。环境变量只对新启动的进程生效，保存后请重新打开命令行窗口。

`--directory` 是必需的安全边界，所有文件和目录操作只能发生在该目录及其子目录中。目录必须预先存在。

也可以不使用初始化脚本、不传 `--directory`，在可交互控制台中启动后由程序询问：

```powershell
java '-Dfile.encoding=UTF-8' -jar target\agent-hello-world-1.0-SNAPSHOT-all.jar
```

无交互控制台时必须传入 `--directory`。

## 交互命令

启动后直接输入自然语言，例如：

```text
列出当前目录中的文件
创建 notes\\today.txt 并写入今天的工作记录
搜索所有扩展名为 txt 的文件
```

特殊命令：

```text
:help       查看帮助
:directory                  查看当前目录边界说明
:directory <新目录>         切换当前会话目录并保存到用户环境变量
:exit       退出
:quit       退出
```

## 运行过程显示

命令行会实时显示以下可审计状态：

- 收到用户指令
- 公开任务计划（以流式方式逐步显示；执行过程中可能根据真实结果调整）
- 任务阶段状态（不显示内部 Agent 名称和工具名称）
- 模型最终答复的流式文本
- 模型长时间处理时的心跳提示
- 最终总结和耗时

命令行只显示简洁的交互信息，不显示时间戳、线程名、日志级别、类名、内部 Agent 名称或工具名称。模型的隐藏思维链不会原样输出，只展示公开任务计划、通用阶段状态和用户可读的流式答复。

最终答复中的常见 Markdown 结构由开源 Flexmark 解析后转换为终端文本，支持标题、加粗、列表、代码、链接和 GFM 表格。无需修改 `start.bat`，也无需安装 Windows 插件；不支持 ANSI 的终端会自动使用纯文本样式。

完整后台日志写入项目目录下的 `logs/personal-assistant.log`，包含工具结果摘要和异常堆栈，不混入用户控制台。

## 当前演示版边界

- 每个助手实例只允许操作一个目录；可通过 `:directory <新目录>` 重新创建当前会话的设备 Agent 并切换边界。
- 文件操作由 Java NIO 完成。
- 程序启动使用 `ProcessBuilder`，不经过 Shell 拼接。
- 关闭程序只允许关闭当前工具实例启动且仍被跟踪的进程。
- Supervisor 使用框架内置 Planner 动态调度能力 Agent，最多调用 10 次；结果审校 Agent 会在未完成时提出修正意见。
- 模型 Key 从用户环境变量读取，不能提交到 GitHub 或写入 EXE。
- PDF、Word、Excel 等文档通过 LangChain4j Apache Tika 解析器读取；扫描件 PDF 仍需要 OCR。

## GitHub Actions Windows 构建

仓库中的 `.github/workflows/build-windows-exe.yml` 会在 Windows Runner 上跳过单元测试执行、构建 fat jar，并生成：

- `PersonalAssistant-portable.zip`：包含运行时的便携版程序目录；

推送到 `main` 分支或手动执行 GitHub Actions 后，可以在对应工作流的 Artifacts 中下载便携版。构建过程不需要 API Key，运行程序时再通过初始化脚本配置。

## 本地生成和测试 Windows 便携版

在 Windows、JDK 25 和 Maven 环境中执行以下命令。Maven 命令必须带项目约定的本地仓库参数：

```powershell
mvn -q -DskipTests package '-Dmaven.repo.local=D:\may\SZH\repository'
$inputDirectory = 'target\jpackage-input-local'
$portableDirectory = 'target\portable-local'
New-Item -ItemType Directory -Force -Path $inputDirectory, $portableDirectory | Out-Null
Copy-Item 'target\agent-hello-world-1.0-SNAPSHOT-all.jar' $inputDirectory -Force
jpackage --type app-image --dest $portableDirectory `
  --name PersonalAssistant --app-version 0.1.0 --vendor lxhgye `
  --description '基于 LangChain4j 的个人 AI 助手' `
  --input $inputDirectory --main-jar 'agent-hello-world-1.0-SNAPSHOT-all.jar' `
  --main-class 'cn.cgn.chat.app.PersonalAssistantCliApplication' --win-console `
  --add-modules java.se,jdk.charsets `
  --java-options '--enable-native-access=ALL-UNNAMED' `
  --java-options '-Dfile.encoding=UTF-8'
```

生成后直接测试帮助和环境变量目录读取：

```powershell
& '.\target\portable-local\PersonalAssistant\PersonalAssistant.exe' '--help'
$env:PERSONAL_ASSISTANT_DIRECTORY = (Resolve-Path 'D:\AgentWorkspace').Path
':exit' | & '.\target\portable-local\PersonalAssistant\PersonalAssistant.exe'
```

确认帮助正常显示、目录权限正确且退出码为 `0` 后，再提交并推送到 GitHub。完整任务测试仍需要配置 `DEEPSEEK_API_KEY`、`SEARCH_API_KEY`，并注意会消耗模型和搜索服务额度。
