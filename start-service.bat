@echo off
chcp 65001 >nul
title Now Playing Backend Service
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-service.ps1"
pause
