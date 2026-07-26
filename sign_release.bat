@echo off
REM Builds the signed .aab for the Play Store. Config comes from .env.
setlocal
call "%~dp0load_env.bat" || exit /b 1

call "%~dp0prompt_passwords.bat" || exit /b 1

call "%~dp0gradlew.bat" bundleRelease ^
    -Pandroid.injected.signing.store.file="%KEYSTORE%" ^
    -Pandroid.injected.signing.store.password="%STORE_PASS%" ^
    -Pandroid.injected.signing.key.alias="%KEY_ALIAS%" ^
    -Pandroid.injected.signing.key.password="%KEY_PASS%"
