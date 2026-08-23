@echo off
chcp 65001 >nul
cd /d "%~dp0"
if defined PERSONAL_ASSISTANT_DIRECTORY (
    java --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -jar "target\agent-hello-world-1.0-SNAPSHOT-all.jar" --directory "%PERSONAL_ASSISTANT_DIRECTORY%"
) else (
    java --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -jar "target\agent-hello-world-1.0-SNAPSHOT-all.jar"
)
