$java = "C:\Program Files\Eclipse Adoptium\jdk-11.0.32.101-hotspot\bin\java.exe"
$userProfile = [System.Environment]::GetFolderPath([System.Environment+SpecialFolder]::UserProfile)
$m2Repo = [System.IO.Path]::Combine($userProfile, ".m2\repository")
$jars = [System.IO.Directory]::GetFiles($m2Repo, "*.jar", [System.IO.SearchOption]::AllDirectories)
$cp = "target\classes;" + ($jars -join ";")

& $java "-Dfile.encoding=UTF-8" -cp $cp com.widdit.nowplaying.NowPlayingApplication
