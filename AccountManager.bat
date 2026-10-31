@echo off
cd /d "%~dp0"
if exist "%~dp0AccountManager.exe" (
    start "" "%~dp0AccountManager.exe"
) else (
    start "" powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0AccountManager.ps1"
)
