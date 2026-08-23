# Windows 文件与程序操作知识库

## 适用范围

本知识库用于个人 Windows 操作助手演示，覆盖文件、目录以及普通用户程序的启动和关闭。Agent 应优先调用已经封装的结构化工具，不得把本文中的命令直接拼接为不受限制的 Shell 脚本。

## 安全规则

1. 查询、列出、搜索和读取属于只读操作，可以直接执行。
2. 创建、写入、追加、复制、移动和重命名属于写操作，执行前应明确目标路径。
3. 覆盖已有文件和删除文件属于高风险操作，必须获得用户明确确认。
4. 禁止递归删除磁盘根目录、用户目录、Windows 目录、Program Files 目录和项目工作区根目录。
5. 操作前使用 `Test-Path` 检查路径；操作后再次检查结果，不能仅根据命令退出码宣称成功。
6. 路径可能包含空格，PowerShell 示例统一使用单引号包裹路径。
7. 用户提供的是字面路径时优先使用 `-LiteralPath`，避免 `[]`、`*`、`?` 被解释为通配符。
8. 启动程序前必须验证可执行文件和参数；关闭程序前必须确认目标进程，禁止根据模糊名称批量结束进程。

## 常用命令速查

| 目标 | PowerShell 命令 | 风险级别 |
|---|---|---|
| 判断路径是否存在 | `Test-Path` | 只读 |
| 查看文件或目录信息 | `Get-Item` | 只读 |
| 列出目录内容 | `Get-ChildItem` | 只读 |
| 搜索文件 | `Get-ChildItem` | 只读 |
| 搜索文本内容 | `Select-String` | 只读 |
| 读取文本文件 | `Get-Content` | 只读 |
| 创建文件或目录 | `New-Item` | 写操作 |
| 覆盖写入文本 | `Set-Content` | 高风险写操作 |
| 追加文本 | `Add-Content` | 写操作 |
| 复制文件或目录 | `Copy-Item` | 写操作 |
| 移动文件或目录 | `Move-Item` | 写操作 |
| 重命名文件或目录 | `Rename-Item` | 写操作 |
| 删除文件或目录 | `Remove-Item` | 高风险写操作 |
| 启动程序 | `Start-Process` | 写操作 |
| 查询程序进程 | `Get-Process` | 只读 |
| 关闭程序 | `Stop-Process` | 高风险写操作 |

## Test-Path：判断路径是否存在

用途：在读取、复制、移动、覆盖和删除前确认路径状态。

```powershell
Test-Path -LiteralPath 'D:\Demo\input.txt'
Test-Path -LiteralPath 'D:\Demo' -PathType Container
Test-Path -LiteralPath 'D:\Demo\input.txt' -PathType Leaf
```

结果为 `True` 表示满足条件，`False` 表示不存在或类型不匹配。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.management/test-path

## Get-Item：查看单个路径的信息

用途：获取文件或目录的名称、完整路径、大小和修改时间等属性。

```powershell
Get-Item -LiteralPath 'D:\Demo\input.txt'
Get-Item -LiteralPath 'D:\Demo\input.txt' | Select-Object FullName,Length,LastWriteTime
```

常见错误：路径不存在时会产生 `ItemNotFoundException`。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.management/get-item

## Get-ChildItem：列出目录或搜索文件

用途：列出目录内容，也可以按名称、类型递归搜索文件。

```powershell
Get-ChildItem -LiteralPath 'D:\Demo'
Get-ChildItem -LiteralPath 'D:\Demo' -File
Get-ChildItem -LiteralPath 'D:\Demo' -Directory
Get-ChildItem -LiteralPath 'D:\Demo' -Filter '*.txt' -File -Recurse
```

注意：递归搜索前应限制允许访问的根目录，并限制最大返回数量。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.management/get-childitem

## Select-String：搜索文本内容

用途：在一个或多个文本文件中搜索指定文字或正则表达式。

```powershell
Select-String -LiteralPath 'D:\Demo\app.log' -Pattern 'ERROR'
Get-ChildItem -LiteralPath 'D:\Demo' -Filter '*.log' -File -Recurse |
    Select-String -Pattern 'timeout'
```

输出通常包含文件路径、行号和命中文本。搜索范围过大时应限制目录、文件类型和结果数量。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.utility/select-string

## Get-Content：读取文本文件

用途：读取文本文件全部内容、开头若干行或末尾若干行。

```powershell
Get-Content -LiteralPath 'D:\Demo\input.txt' -Encoding UTF8
Get-Content -LiteralPath 'D:\Demo\app.log' -Head 50 -Encoding UTF8
Get-Content -LiteralPath 'D:\Demo\app.log' -Tail 100 -Encoding UTF8
```

注意：读取大文件时禁止无上限加载全部内容，应使用 `-Head`、`-Tail` 或流式处理。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.management/get-content

## New-Item：创建文件或目录

用途：创建新目录或空文件。

```powershell
New-Item -ItemType Directory -Path 'D:\Demo\output'
New-Item -ItemType File -Path 'D:\Demo\output\result.txt'
```

操作后应使用 `Test-Path` 验证目标是否存在。目标已存在时，不应擅自增加 `-Force` 覆盖。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.management/new-item

## Set-Content：覆盖写入文本

用途：创建文本文件或完全替换已有文本内容。

```powershell
Set-Content -LiteralPath 'D:\Demo\result.txt' -Value '处理完成' -Encoding UTF8
```

风险：已有内容会被覆盖。执行前必须确认用户确实要求覆盖，并检查目标路径。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.management/set-content

## Add-Content：追加文本

用途：在现有文本文件末尾追加内容；文件不存在时通常会创建文件。

```powershell
Add-Content -LiteralPath 'D:\Demo\app.log' -Value '新增日志' -Encoding UTF8
```

注意：需要保持文件原有编码，避免追加后产生乱码。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.management/add-content

## Copy-Item：复制文件或目录

用途：将文件或目录复制到新位置。

```powershell
Copy-Item -LiteralPath 'D:\Demo\input.txt' -Destination 'D:\Demo\backup\input.txt'
Copy-Item -LiteralPath 'D:\Demo\source' -Destination 'D:\Demo\backup' -Recurse
```

执行前应检查源路径存在、目标父目录存在以及目标是否会被覆盖。目录递归复制必须限制在允许访问的根目录内。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.management/copy-item

## Move-Item：移动文件或目录

用途：将文件或目录移动到另一个目录，也可以同时改变名称。

```powershell
Move-Item -LiteralPath 'D:\Demo\input.txt' -Destination 'D:\Demo\archive\input.txt'
```

操作后应验证源路径已经不存在且目标路径已经存在。目标存在时不得未经确认强制覆盖。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.management/move-item

## Rename-Item：重命名文件或目录

用途：只改变名称，不改变父目录。

```powershell
Rename-Item -LiteralPath 'D:\Demo\old-name.txt' -NewName 'new-name.txt'
```

`-NewName` 应传入新名称而不是另一个目录的完整路径；需要改变父目录时使用 `Move-Item`。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.management/rename-item

## Remove-Item：删除文件或目录

用途：删除单个文件；演示版默认不允许 Agent 递归删除目录。

```powershell
Remove-Item -LiteralPath 'D:\Demo\obsolete.txt'
```

强制规则：

1. 删除前必须获得用户明确确认。
2. 删除前记录规范化后的绝对路径。
3. 默认只允许删除普通文件。
4. 禁止 Agent 自动使用 `-Recurse`、`-Force` 和通配符批量删除。
5. 删除后使用 `Test-Path` 验证文件已经不存在。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.management/remove-item

## Start-Process：启动程序

用途：启动本地可执行文件，可以传入参数和工作目录。

```powershell
Start-Process -FilePath 'C:\Windows\System32\notepad.exe'
Start-Process -FilePath 'C:\Windows\System32\notepad.exe' -ArgumentList 'D:\Demo\notes.txt'
Start-Process -FilePath 'D:\Demo\demo.exe' -WorkingDirectory 'D:\Demo'
```

安全规则：

1. 启动前将程序路径解析为规范化绝对路径，并验证文件真实存在。
2. 程序路径和参数必须分开传递，禁止把用户输入拼成一段 Shell 命令。
3. 禁止启动脚本解释器并传入未经检查的任意脚本，例如 `powershell.exe -EncodedCommand`。
4. 禁止自动使用 `-Verb RunAs` 提升管理员权限。
5. 启动后记录进程号；不能仅根据没有抛出异常就断言程序已经完成业务操作。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.management/start-process

## Get-Process：查找程序进程

用途：关闭程序前查询进程号、进程名和程序路径，避免关闭错误进程。

```powershell
Get-Process -Name 'notepad'
Get-Process -Id 1234 | Select-Object Id,ProcessName,Path,StartTime
```

注意：同一个程序可能存在多个同名进程。Agent 应优先使用启动程序时记录的进程号；无法唯一确认目标时必须询问用户。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.management/get-process

## Stop-Process：关闭程序

用途：根据已确认的进程号关闭普通用户程序。

```powershell
Stop-Process -Id 1234
```

强制规则：

1. 关闭程序属于高风险写操作，必须获得用户明确确认。
2. 优先使用进程号，不允许根据模糊名称或通配符批量关闭进程。
3. 禁止关闭 PID 0 至 PID 4、Windows 关键进程、杀毒软件、安全工具以及 Agent 自身进程。
4. 默认请求程序正常退出，不自动使用 `-Force`；正常退出失败时应报告用户，而不是直接强制终止。
5. 关闭前检查程序是否存在未保存内容。工具无法判断时，必须提示可能丢失数据。
6. 关闭后使用 `Get-Process -Id` 或进程句柄验证进程是否已经退出。

官方文档：https://learn.microsoft.com/powershell/module/microsoft.powershell.management/stop-process

## 常见错误与处理建议

### 路径不存在

现象：`ItemNotFoundException` 或“找不到路径”。

处理：使用 `Test-Path -LiteralPath` 检查；确认盘符、父目录和文件名是否正确。

### 没有访问权限

现象：`UnauthorizedAccessException` 或“拒绝访问”。

处理：不要自动提升管理员权限；先向用户说明目标路径及所需权限。

### 文件正在被占用

现象：文件被另一个进程使用，无法移动、覆盖或删除。

处理：报告占用错误并让用户决定是否关闭相关程序，禁止 Agent 强制终止未知进程。

### 目标已经存在

现象：复制、移动或重命名失败，提示目标文件已存在。

处理：询问用户选择覆盖、换名或取消，禁止默认覆盖。

### 路径包含通配符字符

现象：文件名中的 `[]`、`*`、`?` 被解释为匹配表达式。

处理：对用户提供的具体路径使用 `-LiteralPath`。

### 文本出现乱码

现象：读取或写入后中文字符异常。

处理：演示版统一使用 UTF-8，并在读写命令中显式指定 `-Encoding UTF8`。

### 程序无法启动

现象：找不到可执行文件、缺少运行库、参数错误或权限不足。

处理：检查程序路径、工作目录和参数；保留完整错误信息，不自动提升管理员权限。

### 程序无法正常关闭

现象：进程拒绝退出、正在显示保存确认窗口或进程已无响应。

处理：向用户报告当前状态及数据丢失风险，只有用户再次明确确认后才考虑强制关闭。

## Agent 决策顺序

1. 识别用户目标是查询、读取、搜索、创建、写入、复制、移动、重命名、删除、启动程序还是关闭程序。
2. 将用户路径转换为规范化绝对路径，并检查是否位于允许访问的根目录内。
3. 只读操作可以直接调用工具。
4. 写操作先说明将发生的变化；覆盖和删除必须等待明确确认。
5. 调用结构化文件工具，而不是生成任意 PowerShell 脚本。
6. 操作后再次读取文件属性或检查路径，验证真实结果。
7. 最终回答必须区分“计划执行”“执行成功”“执行失败”和“等待确认”。
8. 启动程序后保存进程号；关闭程序时优先使用该进程号进行精确匹配。
