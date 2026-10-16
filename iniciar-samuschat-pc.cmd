@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0iniciar-samuschat-pc.ps1" %*
if errorlevel 1 pause
