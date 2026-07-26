@echo off
REM Build a SIGNED release APK and install it on the connected device.
REM Note: sign_release.bat builds an .aab for the Play Store; an .aab cannot be
REM installed with adb, so this script builds an apk with the same keystore.
REM Pass --no-build to skip the build and install the last signed APK.
setlocal
call "%~dp0load_env.bat" || exit /b 1
set "APK=%~dp0app\build\outputs\apk\release\app-release.apk"

if /i "%~1"=="--no-build" goto install

if not defined STORE_PASS set /p STORE_PASS=Keystore password:
if not defined KEY_PASS set /p KEY_PASS=Key password:

call "%~dp0gradlew.bat" assembleRelease ^
    -Pandroid.injected.signing.store.file="%KEYSTORE%" ^
    -Pandroid.injected.signing.store.password="%STORE_PASS%" ^
    -Pandroid.injected.signing.key.alias="%KEY_ALIAS%" ^
    -Pandroid.injected.signing.key.password="%KEY_PASS%" || exit /b 1

:install
if not exist "%APK%" (
    echo ERROR: %APK% not found ^(unsigned build?^). Run without --no-build first.
    exit /b 1
)

echo Installing %APK%
adb install -r "%APK%"
if errorlevel 1 goto retry
goto launch

:retry
REM Most likely INSTALL_FAILED_UPDATE_INCOMPATIBLE: the device holds the debug
REM build, whose signature differs. uninstall.bat confirms before erasing data.
echo.
echo Install failed. Removing the existing install and retrying.
call "%~dp0uninstall.bat" || exit /b 1
adb install -r "%APK%" || exit /b 1

:launch
adb shell monkey -p %APP_ID% -c android.intent.category.LAUNCHER 1 >nul
