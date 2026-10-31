param([string]$Java)
$ErrorActionPreference = "Stop"
$manager = Join-Path $PSScriptRoot "AccountManager.exe"
if (-not (Test-Path -LiteralPath $manager)) {
    & "$PSScriptRoot\build-manager.ps1"
}
$start = New-Object System.Diagnostics.ProcessStartInfo
$start.FileName = $manager
$start.WorkingDirectory = $PSScriptRoot
$start.UseShellExecute = $false
$start.CreateNoWindow = $true
if ($Java) {
    if ($Java.Contains('"')) { throw 'Duong dan Java khong hop le.' }
    $start.Arguments = '"' + $Java + '"'
}
[System.Diagnostics.Process]::Start($start) | Out-Null
