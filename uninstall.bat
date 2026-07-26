@echo off
REM Uninstall the app from the connected device. This ERASES app data
REM (high scores, settings). Needed before flashing a release build over a
REM debug build, since the signatures differ.
REM
REM Usage: uninstall.bat [-y]
setlocal
call "%~dp0load_env.bat" || exit /b 1

adb shell pm list packages | findstr /x /c:"package:%APP_ID%" >nul
if errorlevel 1 (
    echo %APP_ID% is not installed. Nothing to do.
    exit /b 0
)

if /i "%~1"=="-y" goto uninstall

echo This uninstalls %APP_ID% and erases its data ^(high scores, settings^).
set /p REPLY=Continue? [y/N]:
if /i not "%REPLY%"=="y" (
    echo Aborted.
    exit /b 1
)

:uninstall
adb uninstall %APP_ID%
