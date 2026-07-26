@echo off
REM Loads KEY=VALUE pairs from .env into the calling script's environment.
REM Lines starting with # are ignored. Do not put spaces around the '='.
if not exist "%~dp0.env" (
    echo ERROR: .env not found. Copy .env.example to .env and fill it in.
    exit /b 1
)
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%~dp0.env") do set "%%A=%%B"
exit /b 0
