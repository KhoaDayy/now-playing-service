$ErrorActionPreference = "SilentlyContinue"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path

# 1. Clear port 9863 conflict if any
Get-Process -Name "NowPlayingService" -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Get-NetTCPConnection -LocalPort 9863 -ErrorAction SilentlyContinue | ForEach-Object {
    Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
}
Start-Sleep -Milliseconds 500

$portBusy = Get-NetTCPConnection -LocalPort 9863 -State Listen -ErrorAction SilentlyContinue
if ($portBusy) {
    Write-Host "==========================================================" -ForegroundColor Red
    Write-Host "CANH BAO: Port 9863 dang bi chiem boi Now Playing cu!" -ForegroundColor Red
    Write-Host "Vui long click chuot phai vao bieu tuong Now Playing o" -ForegroundColor Yellow
    Write-Host "khay he thong (System Tray o goc duoi ben phai man hinh)" -ForegroundColor Yellow
    Write-Host "va chon Exit / Quit (hoac Thoat), sau do chay lai file nay!" -ForegroundColor Yellow
    Write-Host "==========================================================" -ForegroundColor Red
    Read-Host "Nhan Enter de thoat..."
    exit 1
}

# 2. Locate Java 11
$javaBin = "C:\Program Files\Eclipse Adoptium\jdk-11.0.32.101-hotspot\bin\java.exe"
if (-not (Test-Path $javaBin)) {
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
        $javaBin = "$env:JAVA_HOME\bin\java.exe"
    } else {
        $javaCmd = Get-Command java -ErrorAction SilentlyContinue
        if ($javaCmd) { $javaBin = $javaCmd.Source }
        else {
            Write-Host "Loi: Khong tim thay Java 11!" -ForegroundColor Red
            pause
            exit 1
        }
    }
}

Write-Host "-> Su dung Java: $javaBin" -ForegroundColor Green

# 3. Build Classpath
$m2Repo = "$env:USERPROFILE\.m2\repository"
$jars = Get-ChildItem -Path $m2Repo -Filter "*.jar" -Recurse | ForEach-Object { $_.FullName }
$classpath = "$projectRoot\target\classes;" + ($jars -join ";")

Write-Host "-> Dang khoi dong Now Playing Service (Port 9863)..." -ForegroundColor Cyan
Write-Host "-> Nhan Ctrl+C de dung dich vu." -ForegroundColor Yellow
Set-Location $projectRoot
& $javaBin "-Dfile.encoding=UTF-8" "-cp" $classpath "com.widdit.nowplaying.NowPlayingApplication"
