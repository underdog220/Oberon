@echo off
REM Oberon Server — Windows Start-Script
REM Konfiguration ueber Umgebungsvariablen (siehe README)

if "%OBERON_TOKEN%"=="" set OBERON_TOKEN=oberon-dev-token
if "%OBERON_PORT%"=="" set OBERON_PORT=17900

echo === Oberon Server ===
echo Port: %OBERON_PORT%
echo Token: %OBERON_TOKEN:~0,4%***
echo.

java -jar "%~dp0build\libs\oberon-all.jar"
