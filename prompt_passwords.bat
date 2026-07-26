@echo off
REM Sets STORE_PASS and KEY_PASS in the CALLING script's environment, prompting
REM for whichever is not already set (from .env, say). Deliberately no setlocal:
REM the values have to survive back to the caller.
REM
REM Batch has no masked input, so the read is delegated to PowerShell's
REM Read-Host -AsSecureString, which echoes '*' per character. The prompt text is
REM printed here rather than passed to Read-Host, because PowerShell writes its
REM own prompt to stdout when stdout is captured, which would swallow it.
REM
REM Read-Host -AsSecureString needs a real console. Without one it returns
REM nothing, so each prompt falls back to plain visible input rather than
REM handing an empty password to gradle.

if defined STORE_PASS goto keypass
<nul set /p "=Keystore password: "
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "$s = Read-Host -AsSecureString; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))"`) do set "STORE_PASS=%%P"
echo.
if not defined STORE_PASS (
    echo No console available, so the password cannot be masked.
    set /p "STORE_PASS=Keystore password (visible): "
)

:keypass
if defined KEY_PASS goto done
<nul set /p "=Key password: "
for /f "usebackq delims=" %%P in (`powershell -NoProfile -Command "$s = Read-Host -AsSecureString; [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($s))"`) do set "KEY_PASS=%%P"
echo.
if not defined KEY_PASS (
    echo No console available, so the password cannot be masked.
    set /p "KEY_PASS=Key password (visible): "
)

:done
if not defined STORE_PASS (
    echo ERROR: no keystore password entered.
    exit /b 1
)
if not defined KEY_PASS (
    echo ERROR: no key password entered.
    exit /b 1
)
exit /b 0
