@echo off
REM Run the JVM unit tests. Pass extra gradle arguments through, e.g.
REM     test.bat --tests "*TutorialContentTest*"
setlocal
call "%~dp0load_env.bat" || exit /b 1
call "%~dp0gradlew.bat" test %*
