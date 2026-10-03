$javac = "C:\Program Files\Eclipse Adoptium\jdk-11.0.32.101-hotspot\bin\javac.exe"
$userProfile = [System.Environment]::GetFolderPath([System.Environment+SpecialFolder]::UserProfile)
$m2Repo = [System.IO.Path]::Combine($userProfile, ".m2\repository")
$jars = [System.IO.Directory]::GetFiles($m2Repo, "*.jar", [System.IO.SearchOption]::AllDirectories)
$cp = "target\classes;" + ($jars -join ";")

$files = @(
  "src\main\java\com\widdit\nowplaying\entity\SettingsGeneral.java",
  "src\main\java\com\widdit\nowplaying\service\AudioService.java",
  "src\main\java\com\widdit\nowplaying\util\SongMatchingUtil.java",
  "src\main\java\com\widdit\nowplaying\util\SongUtil.java",
  "src\main\java\com\widdit\nowplaying\service\lrclib\LrclibService.java",
  "src\main\java\com\widdit\nowplaying\service\netease\NeteaseMusicService.java",
  "src\main\java\com\widdit\nowplaying\service\qq\QQMusicService.java",
  "src\main\java\com\widdit\nowplaying\service\kugou\KuGouMusicService.java",
  "src\main\java\com\widdit\nowplaying\service\LyricService.java",
  "src\main\java\com\widdit\nowplaying\controller\WebSocketLyricController.java",
  "src\main\java\com\widdit\nowplaying\service\NowPlayingService.java"
)
if (Test-Path "src\main\java\com\widdit\nowplaying\jev") {
  $jevFiles = [System.IO.Directory]::GetFiles("src\main\java\com\widdit\nowplaying\jev", "*.java", [System.IO.SearchOption]::AllDirectories)
  $files += $jevFiles
}

& $javac -encoding UTF-8 -cp $cp -d target\classes $files
if ($LASTEXITCODE -eq 0) {
    Write-Host "BUILD_SUCCESS"
    Copy-Item "src\main\resources\*" -Destination "target\classes" -Recurse -Force
    $jarExe = "C:\Program Files\Eclipse Adoptium\jdk-11.0.32.101-hotspot\bin\jar.exe"
    $bootInfClasses = "target\staging_bootinf\BOOT-INF\classes"
    if (Test-Path "target\staging_bootinf") { Remove-Item "target\staging_bootinf" -Recurse -Force }
    New-Item -ItemType Directory -Path $bootInfClasses -Force | Out-Null
    Copy-Item "target\classes\*" -Destination $bootInfClasses -Recurse -Force
    & $jarExe -uf target\now-playing-service.jar -C target\staging_bootinf BOOT-INF
    Write-Host "JAR_UPDATED_SUCCESS"
} else {
    Write-Host "BUILD_FAILED: $LASTEXITCODE"
}
