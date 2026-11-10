@echo off
setlocal
cd /d "%~dp0services"
call gradlew.bat %*
endlocal
