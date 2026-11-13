@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0services"
set "ARGS=%*"
set "ARGS=!ARGS::services:=:!"
call gradlew.bat !ARGS!
endlocal
