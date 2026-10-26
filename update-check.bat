@echo off
rem Run after a TCL firmware update: what came back on, and is fragit_tv still Home? Offers to fix it.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tv-tools.ps1" -Mode update
pause
