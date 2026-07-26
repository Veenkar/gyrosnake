@echo off
REM Build the debug APK and install it on the connected device.
REM Pass --no-build to skip the build and install the last APK.
setlocal
call "%~dp0load_env.bat" || exit /b 1
set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"

if /i "%~1"=="--no-build" goto install
call "%~dp0gradlew.bat" assembleDebug || exit /b 1

:install
if not exist "%APK%" (
    echo ERROR: %APK% not found. Run without --no-build first.
    exit /b 1
)

echo Installing %APK%
adb install -r "%APK%" || exit /b 1
adb shell monkey -p %APP_ID% -c android.intent.category.LAUNCHER 1 >nul
