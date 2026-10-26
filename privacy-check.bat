@echo off
rem What the TV runs for voice, ACR (content tracking) and telemetry. Read only.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tv-tools.ps1" -Mode privacy
pause
