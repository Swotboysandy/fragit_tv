@echo off
rem Removes frag_the_box from the TV; Android goes back to the TV's own home screen.
set ADB=adb
if exist "%~dp0adb\platform-tools\adb.exe" set ADB="%~dp0adb\platform-tools\adb.exe"
set /p TV=TV IP address:
%ADB% connect %TV%:5555
%ADB% -s %TV%:5555 uninstall com.fragthebox.home
pause
