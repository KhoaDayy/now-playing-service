[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media, ContentType = WindowsRuntime] | Out-Null
$asyncOp = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()
$asTaskGeneric = [System.WindowsRuntimeSystemExtensions].GetMethods() | ? { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' }
$asTask = $asTaskGeneric[0].MakeGenericMethod([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
$task = $asTask.Invoke($null, @($asyncOp))
$mgr = $task.GetAwaiter().GetResult()

$sessions = $mgr.GetSessions()
foreach ($s in $sessions) {
    Write-Host "Session ID:" $s.SourceAppUserModelId
    $info = $s.GetPlaybackInfo()
    Write-Host "Status:" $info.PlaybackStatus
    $propOp = $s.TryGetMediaPropertiesAsync()
    $asTaskProp = $asTaskGeneric[0].MakeGenericMethod([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
    $prop = $asTaskProp.Invoke($null, @($propOp)).GetAwaiter().GetResult()
    Write-Host "Title:" $prop.Title "Artist:" $prop.Artist
    $tl = $s.GetTimelineProperties()
    Write-Host "Pos:" $tl.Position.TotalSeconds "End:" $tl.EndTime.TotalSeconds
    Write-Host "LastUpdated:" $tl.LastUpdatedTime.ToString("o")
    Write-Host "UtcNow:" [DateTimeOffset]::UtcNow.ToString("o")
    $diff = ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalSeconds
    Write-Host "Diff (UtcNow - LastUpdated):" $diff
    Write-Host "Compensated Pos:" ($tl.Position.TotalSeconds + $diff)
    Write-Host "---"
}
