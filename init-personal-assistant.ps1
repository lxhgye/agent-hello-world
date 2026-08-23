# 个人助手初始化脚本：配置当前 Windows 用户的两个 API Key 和允许操作目录。
# 说明：Key 只在内存中短暂转换为明文后写入用户环境变量，不会写入脚本文件。

$ErrorActionPreference = 'Stop'

$deepSeekVariableName = 'DEEPSEEK_API_KEY'
$searchVariableName = 'SEARCH_API_KEY'
$directoryVariableName = 'PERSONAL_ASSISTANT_DIRECTORY'

function Read-RequiredSecret {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Prompt
    )

    while ($true) {
        $secureValue = Read-Host -Prompt $Prompt -AsSecureString
        $valuePointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
        try {
            $value = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($valuePointer)
        }
        finally {
            [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($valuePointer)
        }
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value.Trim()
        }
        Write-Host '输入不能为空，请重新输入。' -ForegroundColor Yellow
    }
}

Write-Host '个人助手初始化' -ForegroundColor Cyan
Write-Host 'API Key 不会回显；配置保存到当前 Windows 用户环境变量。' -ForegroundColor DarkGray
Write-Host ''

$deepSeekApiKey = Read-RequiredSecret '请输入 DEEPSEEK_API_KEY'
$searchApiKey = Read-RequiredSecret '请输入 SEARCH_API_KEY'

while ($true) {
    $directoryInput = Read-Host '请输入允许操作目录的绝对路径'
    if ([string]::IsNullOrWhiteSpace($directoryInput)) {
        Write-Host '目录不能为空，请重新输入。' -ForegroundColor Yellow
        continue
    }

    $directoryItem = Get-Item -LiteralPath $directoryInput.Trim() -ErrorAction SilentlyContinue
    if ($null -ne $directoryItem -and $directoryItem.PSIsContainer) {
        $allowedDirectory = $directoryItem.FullName
        break
    }
    Write-Host '目录不存在或不是目录，请重新输入。' -ForegroundColor Yellow
}

[Environment]::SetEnvironmentVariable($deepSeekVariableName, $deepSeekApiKey, 'User')
[Environment]::SetEnvironmentVariable($searchVariableName, $searchApiKey, 'User')
[Environment]::SetEnvironmentVariable($directoryVariableName, $allowedDirectory, 'User')

Write-Host ''
Write-Host '初始化完成，已配置当前用户环境变量：' -ForegroundColor Green
Write-Host "  $deepSeekVariableName"
Write-Host "  $searchVariableName"
Write-Host "  $directoryVariableName = $allowedDirectory"
Write-Host ''
Write-Host '请关闭当前命令行窗口并重新打开，再运行 start.bat。' -ForegroundColor Cyan
