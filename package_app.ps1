$ErrorActionPreference = "Stop"

Write-Host "=== 1. BIEN DICH PORTABLE LAUNCHER ==="
$csc = "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe"
$launcherOutput = Join-Path $PSScriptRoot "NowPlayingService_portable.exe"
& $csc /target:winexe /reference:System.Management.dll "/out:$launcherOutput" (Join-Path $PSScriptRoot "portable_launcher.cs")
if ($LASTEXITCODE -ne 0) {
    throw "Loi bien dich portable launcher!"
}

$desktop = [System.Environment]::GetFolderPath([System.Environment+SpecialFolder]::Desktop)
$stagingDir = Join-Path $desktop "NowPlaying-v2.2.0-Portable"
$zipPath = Join-Path $desktop "NowPlaying-v2.2.0-Portable.zip"
$sourceDir = "C:\Program Files\Now Playing"
$jarSource = Join-Path $PSScriptRoot "target\now-playing-service.jar"

# Refuse an old jar left by the manual build script or a different Maven finalName.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = [System.IO.Compression.ZipFile]::OpenRead($jarSource)
try {
    $classesDir = Join-Path $PSScriptRoot "target\classes"
    $compiledClasses = @(Get-ChildItem -LiteralPath $classesDir -Filter '*.class' -Recurse)
    if ($compiledClasses.Count -eq 0) { throw "No compiled classes. Run Maven package first." }
    foreach ($compiledClass in $compiledClasses) {
        $relativeClass = $compiledClass.FullName.Substring($classesDir.Length + 1).Replace('\', '/')
        $entry = $jar.GetEntry("BOOT-INF/classes/$relativeClass")
        if ($null -eq $entry) { throw "Missing compiled class in jar: $relativeClass. Run Maven package first." }
        $stream = $entry.Open()
        $sha = [System.Security.Cryptography.SHA256]::Create()
        try { $entryHash = [BitConverter]::ToString($sha.ComputeHash($stream)).Replace("-", "") }
        finally { $stream.Dispose(); $sha.Dispose() }
        $compiledHash = (Get-FileHash -LiteralPath $compiledClass.FullName -Algorithm SHA256).Hash
        if ($entryHash -ne $compiledHash) { throw "Stale jar class: $relativeClass. Run Maven package first." }
    }
} finally { $jar.Dispose() }

Write-Host "=== 2. CHUAN BI THU MUC DONG GOI: $stagingDir ==="
if (Test-Path $stagingDir) {
    $backupDir = Join-Path $PSScriptRoot ("target\portable-backups\" + (Get-Date -Format "yyyyMMdd-HHmmss"))
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
    foreach ($name in @("now-playing-service.jar", "NowPlayingService.exe", "HUONG-DAN-SU-DUNG.txt")) {
        $oldFile = Join-Path $stagingDir $name
        if (Test-Path -LiteralPath $oldFile) { Copy-Item -LiteralPath $oldFile -Destination $backupDir }
    }
    Write-Host "Backup previous backend: $backupDir"
}
New-Item -ItemType Directory -Path $stagingDir -Force | Out-Null

function Copy-PortableFile([string]$sourcePath, [string]$destinationPath) {
    if ((Test-Path -LiteralPath $destinationPath) -and
        (Get-FileHash -LiteralPath $sourcePath).Hash -eq (Get-FileHash -LiteralPath $destinationPath).Hash) {
        return
    }
    New-Item -ItemType Directory -Path (Split-Path -Parent $destinationPath) -Force | Out-Null
    $replacementPath = $destinationPath + '.' + [guid]::NewGuid().ToString('N') + '.new'
    Copy-Item -LiteralPath $sourcePath -Destination $replacementPath -ErrorAction Stop
    $previousPath = $null
    try {
        # Rename the old file so an open executable/JAR retains its original bytes.
        if (Test-Path -LiteralPath $destinationPath) {
            $fileBackupDir = Join-Path $PSScriptRoot 'target\portable-backups\replaced-files'
            New-Item -ItemType Directory -Path $fileBackupDir -Force | Out-Null
            $previousPath = Join-Path $fileBackupDir (([guid]::NewGuid().ToString('N')) + '-' + (Split-Path -Leaf $destinationPath))
            try {
                Move-Item -LiteralPath $destinationPath -Destination $previousPath -ErrorAction Stop
            } catch {
                if ($sourcePath -eq $jarSource -and $destinationPath -eq (Join-Path $stagingDir 'now-playing-service.jar')) {
                    $pendingJar = Join-Path $stagingDir 'now-playing-service.next.jar'
                    Copy-PortableFile $sourcePath $pendingJar
                    Write-Host 'The open backend holds its JAR. The new launcher will apply the pending JAR after restart.'
                    return
                }
                throw
            }
        }
        Move-Item -LiteralPath $replacementPath -Destination $destinationPath -ErrorAction Stop
    } catch {
        if ($null -ne $previousPath -and (Test-Path -LiteralPath $previousPath)) {
            Move-Item -LiteralPath $previousPath -Destination $destinationPath -Force
        }
        throw
    } finally {
        if (Test-Path -LiteralPath $replacementPath) { Remove-Item -LiteralPath $replacementPath -Force }
    }
}

function Copy-PortableTree([string]$sourcePath, [string]$destinationPath, [switch]$SkipMusicStatus) {
    New-Item -ItemType Directory -Path $destinationPath -Force | Out-Null
    foreach ($sourceFile in Get-ChildItem -LiteralPath $sourcePath -File -Recurse) {
        $relativePath = $sourceFile.FullName.Substring($sourcePath.Length + 1)
        if ($SkipMusicStatus -and $relativePath -like 'AudioService\GetMusicStatus.*') { continue }
        Copy-PortableFile $sourceFile.FullName (Join-Path $destinationPath $relativePath)
    }
}

Write-Host "=== 3. COPY CAC TEP CAN THIET ==="
# 3.1 Copy Now Playing.exe
Copy-PortableFile (Join-Path $sourceDir "Now Playing.exe") (Join-Path $stagingDir "Now Playing.exe")

# 3.2 Copy portable NowPlayingService.exe
Copy-PortableFile (Join-Path $PSScriptRoot "NowPlayingService_portable.exe") (Join-Path $stagingDir "NowPlayingService.exe")

# 3.3 Copy now-playing-service.jar (fat jar voi toan bo logic da fix)
Copy-PortableFile $jarSource (Join-Path $stagingDir "now-playing-service.jar")

# 3.4 Copy JRE
Write-Host "Copying JRE..."
Copy-PortableTree (Join-Path $sourceDir "jre") (Join-Path $stagingDir "jre")

# 3.5 Copy Assets
Write-Host "Copying Assets..."
Copy-PortableTree (Join-Path $sourceDir "Assets") (Join-Path $stagingDir "Assets") -SkipMusicStatus
# Overwrite with patched GetMusicStatus from repo
foreach ($musicStatusFile in Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot "Assets\AudioService") -Filter 'GetMusicStatus.*' -File) {
    Copy-PortableFile $musicStatusFile.FullName (Join-Path $stagingDir "Assets\AudioService\$($musicStatusFile.Name)")
}

# 3.6 Copy Plugins, Public, Outputs
Write-Host "Copying Plugins, Public, Outputs..."
if (Test-Path (Join-Path $sourceDir "Plugins")) {
    Copy-PortableTree (Join-Path $sourceDir "Plugins") (Join-Path $stagingDir "Plugins")
}
if (Test-Path (Join-Path $sourceDir "Public")) {
    Copy-PortableTree (Join-Path $sourceDir "Public") (Join-Path $stagingDir "Public")
}
New-Item -ItemType Directory -Path (Join-Path $stagingDir "Outputs") -Force | Out-Null

# 3.7 Settings & Config
New-Item -ItemType Directory -Path (Join-Path $stagingDir "Settings") -Force | Out-Null
$cleanSettings = @'
{
  "autoLaunchHomePage": false,
  "deviceId": "default",
  "deviceName": "Default Audio Device",
  "fallbackPlatform": "netease",
  "fallbackPlatformEnabled": false,
  "platform": "browser",
  "pollInterval": 100,
  "progressOffsetMs": 0,
  "runAtStartup": false,
  "smtc": true,
  "updateCheckFreq": 0,
  "weSingCachePath": ""
}
'@
if (-not (Test-Path -LiteralPath (Join-Path $stagingDir "Settings\settings.json"))) {
    Set-Content -LiteralPath (Join-Path $stagingDir "Settings\settings.json") -Value $cleanSettings -Encoding UTF8
}

$cleanConfig = @'
{
  "announcementTimestamp": 0,
  "checkUpdateTimestamp": 0,
  "mainWindow": {
    "width": 1916,
    "height": 1148,
    "maximized": false
  },
  "desktopWidgetSettings": {
    "currentTab": "lyric",
    "profiles": {
      "song": {
        "visible": false,
        "displayMode": "alwaysOnTop",
        "positionLocked": true,
        "sizeLocked": true,
        "width": 850,
        "height": 450,
        "x": 80,
        "y": 80,
        "urlSuffix": ""
      },
      "lyric": {
        "visible": true,
        "displayMode": "alwaysOnTop",
        "positionLocked": true,
        "sizeLocked": true,
        "width": 850,
        "height": 450,
        "x": 80,
        "y": 80,
        "urlSuffix": ""
      },
      "player": {
        "visible": false,
        "displayMode": "alwaysOnTop",
        "positionLocked": true,
        "sizeLocked": true,
        "width": 850,
        "height": 450,
        "x": 80,
        "y": 80,
        "urlSuffix": ""
      }
    }
  },
  "debug": false
}
'@
if (-not (Test-Path -LiteralPath (Join-Path $stagingDir "config.json"))) {
    Set-Content -LiteralPath (Join-Path $stagingDir "config.json") -Value $cleanConfig -Encoding UTF8
}

# 3.8 File Huong Dan
$readme = @'
=============================================================
           NOW PLAYING v2.2.0 (PORTABLE - DA FIX LYRICS)
=============================================================

1. GIOI THIEU:
- Ban Now Playing nay da duoc toi uu hoa va sua loi:
  + Nguon chinh (NetEase/QQ) duoc thu truoc; LRCLIB luon la nguon du phong cuoi.
  + Che do tu dong so sanh NetEase va QQ; che do thu cong thu nguon da chon truoc.
  + LRCLIB uu tien Synced Lyrics ben trong ket qua cua chinh LRCLIB.
  + Tu dong loc bo cac tu khoa nhieu (vietsub, remix, drill, karaoke, audio lyrics,...).
  + Offset rieng cho moi app qua HTTP hoac WebSocket, khong sua lyric cache goc.

2. CACH SU DUNG (CUC KY DON GIAN):
- Ban KHONG can cai dat, KHONG can cai Java (da tich hop san JRE).
- Chi can giai nen thu muc nay ra bat ky dau (Desktop, o D, v.v.).
- Nhap dup vao file "Now Playing.exe" de bat dau su dung!
- Ung dung se tu dong chay ngam service va bat widget hien thi loi bai hat len man hinh.
- Neu cap nhat khi app dang mo: thoat Now Playing o khay he thong, roi mo lai.
- Neu co now-playing-service.next.jar, launcher se tu thay backend khi JAR cu da dong.

3. OFFSET LYRICS CHO APP NGOAI:
- HTTP: http://localhost:9863/api/lyric?offsetMs=1500
- WebSocket: ws://localhost:9863/api/ws/lyric?offsetMs=1500
- +1500: loi hien muon hon 1,5 giay; -1500: loi hien som hon 1,5 giay.
- Mac dinh 0; gia tri ngoai +/-600000 ms duoc gioi han ve bien nay.
- Ap dung cho LRC, ban dich va karaoke; khong doi tien do phat nhac.
- Moi app dat offset rieng trong URL; doi offset WebSocket bang cach ket noi lai.
- Offset co dinh bu duoc intro lech; ban remix/cat ghep co the can lyrics rieng.
- Jev dung TYPESAFE_API_KEY; khong co key/ket noi thi quay ve cach so khop truyen thong.

4. LUU Y:
- Khi bat nhac tren trinh duyet (Chrome/Edge/CocCoc/Brave), hay chac chan ban dang nghe tren tab YouTube Music hoac YouTube.
- Neu ban muon chinh vi tri/kich thuoc/giao dien widget: nhap chuot phai vao bieu tuong Now Playing o khay he thong (System Tray - goc duoi ben phai man hinh) va chon "Cai dat".
=============================================================
'@
Set-Content -Path (Join-Path $stagingDir "HUONG-DAN-SU-DUNG.txt") -Value $readme -Encoding UTF8

Write-Host "=== 4. TAO FILE ZIP NEN: $zipPath ==="
$temporaryZip = Join-Path $PSScriptRoot ("target\NowPlaying-Portable-" + (Get-Date -Format "yyyyMMdd-HHmmss") + ".zip")
# Use the freshly built JAR in the archive, even if the open app requires a deferred update.
# Runtime logs and updater backups are not part of the portable package.
$archive = [System.IO.Compression.ZipFile]::Open($temporaryZip, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($packageFile in Get-ChildItem -LiteralPath $stagingDir -File -Recurse) {
        $entryName = $packageFile.FullName.Substring($stagingDir.Length + 1).Replace('\', '/')
        if ($packageFile.Name -like '*.log' -or $packageFile.Name -like '*.new' -or
            $packageFile.Name -like 'now-playing-service.jar.previous*' -or
            $entryName -eq 'now-playing-service.next.jar') { continue }
        $entrySource = if ($entryName -eq 'now-playing-service.jar') { $jarSource } else { $packageFile.FullName }
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $entrySource,
            $entryName, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose() }
Move-Item -LiteralPath $temporaryZip -Destination $zipPath -Force

Write-Host "=== DONG GOI HOAN TAT! ==="
Write-Host "File Zip da duoc tao tai: $zipPath"
$zipItem = Get-Item $zipPath
Write-Host ("Dung luong file: {0:N2} MB" -f ($zipItem.Length / 1MB))
