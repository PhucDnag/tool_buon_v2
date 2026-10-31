param(
    [string]$Java = 'C:\Program Files\Java\jre1.8.0_503\bin\java.exe',
    [string]$Emulator = "$PSScriptRoot\tools\microemulator.jar",
    [string]$GameJar = "$PSScriptRoot\dist\KnightOnline_PhucHso_v2.jar",
    [string]$WindowStyle = 'Hidden',
    [switch]$RestartOnCleanExit,
    [switch]$Check
)
$ErrorActionPreference = 'Stop'
foreach ($inputPath in @($Java, $Emulator, $GameJar)) {
    if (-not (Test-Path -LiteralPath $inputPath -PathType Leaf)) { throw "Khong tim thay: $inputPath" }
    if ($inputPath.Contains('"')) { throw 'Duong dan khong duoc chua dau ngoac kep.' }
}
if ($Check) {
    Write-Output 'Launcher hop le. Chay khong co -Check de mo MicroEmulator va giam sat tien trinh.'
    return
}
$javaPath = (Resolve-Path -LiteralPath $Java).Path
$emulatorPath = (Resolve-Path -LiteralPath $Emulator).Path
$gamePath = (Resolve-Path -LiteralPath $GameJar).Path
$javawPath = Join-Path (Split-Path -Parent $javaPath) 'javaw.exe'
if (Test-Path -LiteralPath $javawPath) { $javaPath = $javawPath }
$restartCount = 0
Write-Output 'Dong game binh thuong de dung. Ctrl+C de dung giam sat. Bat Tu ket noi lai va Auto buon trong menu de tiep tuc sau crash.'
while ($true) {
    $startedAt = [DateTime]::UtcNow
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = $javaPath
    $start.Arguments = '-jar "' + $emulatorPath + '" --resizableDevice 300 300 "' + $gamePath + '"'
    $start.WorkingDirectory = $PSScriptRoot
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.WindowStyle = $WindowStyle
    $emulatorProcess = [System.Diagnostics.Process]::Start($start)
    $emulatorProcess.WaitForExit()
    $exitStatus = $emulatorProcess.ExitCode
    $emulatorProcess.Dispose()
    if ($exitStatus -eq 0 -and -not $RestartOnCleanExit) { break }
    if (([DateTime]::UtcNow - $startedAt).TotalMinutes -ge 10) { $restartCount = 0 }
    $retrySeconds = [Math]::Min(300, 10 * [Math]::Pow(2, [Math]::Min($restartCount, 5)))
    $restartCount++
    Write-Output "MicroEmulator da thoat (ma $exitStatus). Mo lai sau $retrySeconds giay."
    Start-Sleep -Seconds $retrySeconds
}
