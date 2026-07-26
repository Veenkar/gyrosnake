@echo off
REM Thin gradlew wrapper that sets JAVA_HOME from .env. Pass any gradle task, e.g.
REM     build_release.bat assembleDebug
setlocal
call "%~dp0load_env.bat" || exit /b 1
call "%~dp0gradlew.bat" %*
