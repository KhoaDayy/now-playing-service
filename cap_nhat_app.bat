@echo off
:: Request Administrator privileges
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
if '%errorlevel%' NEQ '0' (
    echo Dang yeu cau quyen Administrator de cap nhat Now Playing...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

chcp 65001 >nul
title Cap nhat Now Playing

echo 1. Dang dong cac tien trinh Now Playing cu...
taskkill /F /IM "Now Playing.exe" >nul 2>&1
taskkill /F /IM "NowPlayingService.exe" >nul 2>&1

set "INSTALL_DIR=C:\Program Files\Now Playing"
set "SRC_DIR=%~dp0"

echo 2. Sao luu file cu...
if not exist "%INSTALL_DIR%\NowPlayingService.exe.original" (
    copy /y "%INSTALL_DIR%\NowPlayingService.exe" "%INSTALL_DIR%\NowPlayingService.exe.original"
)

echo 3. Cap nhat ban code moi nhat vao Now Playing...
copy /y "%SRC_DIR%NowPlayingService_new.exe" "%INSTALL_DIR%\NowPlayingService.exe"

echo.
echo ========================================================
echo DA CAP NHAT THANH CONG!
echo Tu gio tro di, ban chi can mo duy nhat file "Now Playing.exe"
echo nhu binh thuong. No se tu dong chay toan bo code moi!
echo ========================================================
echo.
pause
